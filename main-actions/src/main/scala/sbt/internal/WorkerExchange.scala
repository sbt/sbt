/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import org.scalasbt.shadedgson.com.google.gson.Gson
import java.io.*
import java.net.{ InetAddress, ServerSocket }
import java.util.Scanner
import java.util.concurrent.atomic.AtomicReference
import sbt.io.IO
import sbt.internal.io.Retry
import sbt.internal.worker1.*
import sbt.testing.Framework
import scala.sys.process.{ BasicIO, Process, ProcessIO }
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.duration.*
import scala.util.control.NonFatal

object WorkerExchange:
  val listeners: mutable.ListBuffer[WorkerResponseListener] = ListBuffer.empty

  private val loopback = InetAddress.getByName(null)

  /**
   * Start a worker process.
   */
  def startWorker(
      fo: ForkOptions,
      extraCp: Seq[File],
      connectionType: WorkerConnection,
  ): WorkerProxy =
    // put extraCp first so we can shadow the WorkerMain class
    val fullCp = extraCp ++ Seq(
      IO.classLocationPath(classOf[WorkerMain]).toFile,
      IO.classLocationPath(classOf[Framework]).toFile,
      IO.classLocationPath(classOf[Gson]).toFile,
    )
    val inputRef = Promise[OutputStream]()
    // Completed once everything the worker sent has been handed to the listeners.
    val streamEnd = Promise[Unit]()
    val bound = AtomicReference[WorkerResponseListener](null)
    def deliver(line: String): Unit =
      bound.get() match
        case null => notifyListeners(line)
        case wl   => wl(line)
    val socketOpt = connectionType match
      case WorkerConnection.Tcp =>
        val serverSocket = Retry(ServerSocket(0, 1, loopback))
        val accepter = Thread(() => {
          try
            // Quiet on purpose: this throws when startWorker gives up on a worker that never
            // connected, and the caller is already reporting why the fork failed.
            val accepted =
              try Some(serverSocket.accept())
              catch case NonFatal(_) => None
            accepted.foreach: socket =>
              try
                inputRef.success(socket.getOutputStream())
                val scanner = Scanner(socket.getInputStream(), "UTF-8")
                while scanner.hasNextLine() do deliver(scanner.nextLine())
              finally
                // Closing the ServerSocket does not close what it accepted.
                socket.close()
          finally
            streamEnd.trySuccess(())
            ()
        })
        accepter.setName("sbt-fork-test-response-reader")
        accepter.setPriority(Thread.NORM_PRIORITY + 1)
        accepter.start()
        Some(serverSocket)
      case _ => None
    val options = Seq(
      "-classpath",
      fullCp.mkString(File.pathSeparator),
      classOf[WorkerMain].getCanonicalName,
    ) ++
      (socketOpt match
        case Some(s) => Seq("--tcp", s.getLocalPort().toString())
        case _       => Nil)
    val onStdoutLine: String => Unit = connectionType match
      case WorkerConnection.Stdio => deliver
      case _                      => (line) => scala.Console.out.println(line)
    val readStdout = BasicIO.processFully(onStdoutLine)
    val processIo = ProcessIO(
      in = (input) =>
        (connectionType match
          case WorkerConnection.Stdio => inputRef.success(input)
          case _                      => ()
        ),
      // Over Stdio the notifications arrive on stdout; over Tcp the accepter thread owns the signal.
      out = connectionType match
        case WorkerConnection.Stdio =>
          (stream) =>
            try readStdout(stream)
            finally
              streamEnd.trySuccess(())
              ()
        case _ => readStdout,
      err = BasicIO.processFully((line) => scala.Console.err.println(line)),
    )
    val forkWithIo = fo.withOutputStrategy(OutputStrategy.CustomInputOutput(processIo))
    val p = Fork.java.fork(forkWithIo, options)
    val forkTimeout = fo.connectionTimeout.getOrElse(30.seconds)
    val input =
      try Await.result(inputRef.future, forkTimeout)
      catch
        case NonFatal(e) =>
          // No WorkerProxy exists yet to close these.
          socketOpt.foreach(_.close())
          p.destroy()
          throw e
    WorkerProxy(input, p, options, socketOpt, streamEnd.future, bound)

  def registerListener(listener: WorkerResponseListener): Unit =
    synchronized:
      listeners.append(listener)
      ()

  def unregisterListener(listener: WorkerResponseListener): Unit =
    synchronized:
      if listeners.contains(listener) then listeners.remove(listeners.indexOf(listener))
      ()

  // Snapshot under the lock, call listeners outside it.
  private def snapshot(): Vector[WorkerResponseListener] = synchronized(listeners.toVector)

  /** Broadcast handler for connections no listener has claimed — see [[WorkerProxy.bind]]. */
  def notifyListeners(line: String): Unit =
    snapshot().foreach: wl =>
      wl(line)

  def notifyExit(p: Process): Unit =
    snapshot().foreach: wl =>
      wl.notifyExit(p)
end WorkerExchange

class WorkerProxy(
    input: OutputStream,
    val process: Process,
    val options: Seq[String],
    serverSocket: Option[ServerSocket],
    // Completes when the stream carrying this worker's notifications reaches its end, which is the
    // only point at which everything the worker sent has been handed to the listeners.
    streamEnd: Future[Unit],
    // Where [[bind]] records this connection's owner, shared with the thread that reads it.
    boundListener: AtomicReference[WorkerResponseListener],
) extends AutoCloseable:
  def this(
      input: OutputStream,
      process: Process,
      options: Seq[String],
      serverSocket: Option[ServerSocket],
  ) =
    this(
      input,
      process,
      options,
      serverSocket,
      Future.unit,
      AtomicReference[WorkerResponseListener](null)
    )

  /**
   * Claims this connection, so its lines and its process exit go only to `wl` instead of being
   * broadcast. Call before the request that makes the worker start writing.
   */
  private[sbt] def bind(wl: WorkerResponseListener): Unit = boundListener.set(wl)

  /**
   * Blocks until every notification this worker sent has reached the listeners, which anything
   * judging a dead worker's state has to do first. The bound is a backstop, not a deadline.
   */
  private[sbt] def awaitStreamEnd(): Unit =
    try
      Await.ready(streamEnd, 30.seconds)
      ()
    catch case NonFatal(_) => ()

  lazy val inputStream = PrintStream(input)
  def close(): Unit =
    input.close()
    serverSocket.foreach(_.close())
  def blockForExitCode(): Int =
    if !process.isAlive() then process.exitValue()
    else Fork.blockForExitCode(process)

  /** print a line into stdin of the worker process. */
  def println(str: String): Unit =
    inputStream.println(str)
    inputStream.flush()

  val watch = Thread(() => {
    while process.isAlive() do Thread.sleep(100)
    // Exits go to the owner once bound; broadcast covers an exit before any bind.
    boundListener.get() match
      case null => WorkerExchange.notifyExit(process)
      case wl   => wl.notifyExit(process)
  })
  watch.start()
end WorkerProxy

abstract class WorkerResponseListener extends Function1[String, Unit]:
  def notifyExit(p: Process): Unit

enum WorkerConnection:
  case Stdio
  case Tcp
