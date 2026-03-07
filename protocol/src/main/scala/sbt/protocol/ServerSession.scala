/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.protocol

import java.io.{ File, IOException }
import java.net.{ Socket, SocketTimeoutException }
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeoutException
import scala.concurrent.duration.*
import sbt.io.IO
import sbt.internal.util.JoinThread.*

/**
 * Base class for JSON-RPC communication over a socket. Manages a background
 * read thread that continuously reads frames and delegates them to [[onFrame]].
 *
 * @param socket     the connected socket
 * @param threadName name for the background read thread
 */
abstract class ServerSession private[sbt] (
    socket: Socket,
    threadName: String
) extends AutoCloseable {

  /** Controls the read loop; set to `false` to stop reading from the socket. */
  private val running = new AtomicBoolean(true)

  /** Guards [[close]] idempotency — ensures cleanup runs exactly once. */
  private val closed = new AtomicBoolean(false)

  /** Output stream for sending messages. Protected for subclass access. */
  protected final val out = socket.getOutputStream

  /**
   * Called by the read thread for each incoming frame.
   * Default is a no-op; subclasses override to enqueue or dispatch.
   */
  protected def onFrame(frame: Seq[Byte]): Unit

  /**
   * Called exactly once during [[close]], after the socket and output stream
   * have been closed but before the read thread is joined.
   */
  protected def onClose(): Unit = ()

  /**
   * Background thread that continuously reads JSON-RPC frames from the socket
   * and passes them to [[onFrame]]. Uses a 5-second socket timeout so the
   * thread periodically re-checks the [[running]] flag.
   */
  private val readThread = new Thread(threadName) {
    setDaemon(true)
    override def run(): Unit = {
      try {
        val in = socket.getInputStream
        socket.setSoTimeout(5000)
        while (running.get) {
          try {
            val frame = JsonRpcReader.read(in, running, onHeader = None)
            if (running.get) onFrame(frame)
          } catch {
            case _: SocketTimeoutException => // re-check running
            case _: IOException            => running.set(false)
          }
        }
      } finally {
        close()
      }
    }
  }
  readThread.start()

  /** Returns `true` if the session is still actively reading from the socket. */
  final def isRunning: Boolean = running.get

  /**
   * Closes the socket connection and stops the read thread.
   *
   * Idempotent — safe to call multiple times. Calls [[onClose]] after closing
   * I/O and before joining the read thread. When called from the read thread
   * itself (via the `finally` block), skips the thread join to avoid deadlock.
   */
  override def close(): Unit = if (closed.compareAndSet(false, true)) {
    running.set(false)
    try {
      out.close()
      socket.close()
    } catch { case _: IOException => }
    onClose()
    if (Thread.currentThread() != readThread)
      readThread.joinFor(ServerSession.ThreadDestroyTimeout)
  }
}

object ServerSession {

  /** Default timeout for awaiting a JSON-RPC response. */
  private[sbt] val ResponseTimeout: FiniteDuration = 1.minutes

  /** Timeout for waiting for the sbt portfile to be created. */
  private[sbt] val PortfileTimeout: FiniteDuration = 1.minute

  /** Timeout for the initialize handshake with the sbt server. */
  private[sbt] val InitializeTimeout: FiniteDuration = 10.seconds

  /** Time to wait for the sbt process to exit gracefully after sending shutdown. */
  private[sbt] val GracefulShutdownTimeout: FiniteDuration = 5.seconds

  /** Time to wait for the sbt process to exit after calling destroy(). */
  private[sbt] val DestroyTimeout: FiniteDuration = 10.seconds

  /** Time to wait for the read thread to finish when closing the session. */
  private[sbt] val ThreadDestroyTimeout: FiniteDuration = 5.seconds

  /** Interval between progress log messages while waiting for the portfile. */
  private[sbt] val PortfileLogInterval: FiniteDuration = 10.seconds

  /**
   * Connects to a running sbt server using the given portfile.
   *
   * @param portfile the `active.json` portfile created by the sbt server
   * @return a connected [[ServerSessionClient]] ready for [[ServerSessionClient.initialize]]
   */
  def connect(portfile: File): ServerSessionClient = {
    val (socket, _) = ClientSocket.socket(portfile, false)
    new ServerSessionClient(socket)
  }

  /** Waits for the portfile using [[PortfileTimeout]] and no logging. */
  def waitForPortfile(portfile: File, isAlive: => Boolean): Unit =
    waitForPortfile(portfile, isAlive, PortfileTimeout, _ => ())

  /** Waits for the portfile with the given duration and no logging. */
  def waitForPortfile(portfile: File, isAlive: => Boolean, duration: FiniteDuration): Unit =
    waitForPortfile(portfile, isAlive, duration, _ => ())

  /**
   * Blocks until the sbt server portfile is created and non-empty.
   *
   * @param portfile the expected portfile path
   * @param isAlive  check whether the server process is still running
   * @param duration maximum time to wait
   * @param log      callback for progress messages
   * @throws TimeoutException if the portfile is not created within `duration`
   * @throws RuntimeException if the server process exits before the portfile appears
   */
  def waitForPortfile(
      portfile: File,
      isAlive: => Boolean,
      duration: FiniteDuration,
      log: String => Unit
  ): Unit = {
    def portfileIsEmpty(): Boolean =
      try IO.read(portfile).isEmpty
      catch { case _: IOException => true }

    log(s"Waiting up to $duration for sbt to be ready ...")

    val deadline = duration.fromNow
    var nextLog = PortfileLogInterval.fromNow

    while (portfileIsEmpty() && !deadline.isOverdue && isAlive) {
      if (nextLog.isOverdue) {
        log("Still waiting for sbt ...")
        nextLog = PortfileLogInterval.fromNow
      }
      Thread.sleep(10)
    }

    if (deadline.isOverdue)
      throw new TimeoutException(
        s"${portfile.getAbsolutePath} was not created within $duration"
      )

    if (!isAlive)
      throw new RuntimeException("sbt process unexpectedly terminated")
  }

}
