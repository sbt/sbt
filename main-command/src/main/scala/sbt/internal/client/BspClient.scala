/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.client

import java.io.{ File, InputStream, OutputStream }
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

import sbt.Exit
import sbt.io.syntax._
import sbt.protocol.ClientSocket

import scala.util.control.NonFatal
import java.lang.ProcessBuilder.Redirect

class BspClient private (sbtServer: Socket) {
  private def run(): Exit = Exit(BspClient.bspRun(sbtServer))
}

object BspClient {
  private[sbt] def bspRun(sbtServer: Socket): Int = {
    val lock = new AnyRef
    val terminated = new AtomicBoolean(false)
    transferTo(terminated, lock, sbtServer.getInputStream, System.out).start()
    transferTo(terminated, lock, System.in, sbtServer.getOutputStream).start()
    try {
      lock.synchronized {
        while (!terminated.get) lock.wait()
      }
      0
    } catch { case _: Throwable => 1 } finally sbtServer.close()
  }

  private[sbt] def transferTo(
      terminated: AtomicBoolean,
      lock: AnyRef,
      input: InputStream,
      output: OutputStream
  ): Thread = {
    val thread = new Thread {
      override def run(): Unit = {
        val buffer = Array.ofDim[Byte](1024)
        try {
          while (!terminated.get) {
            val size = input.read(buffer)
            if (size == -1) {
              terminated.set(true)
            } else {
              output.write(buffer, 0, size)
              output.flush()
            }
          }
          input.close()
          output.close()
        } catch {
          case _: InterruptedException => terminated.set(true)
          case NonFatal(_)             => ()
        } finally {
          lock.synchronized {
            terminated.set(true)
            lock.notify()
          }
        }
      }
    }
    thread.setDaemon(true)
    thread
  }
  def run(configuration: xsbti.AppConfiguration): Exit = {
    val baseDirectory = configuration.baseDirectory
    val portFile = baseDirectory / "project" / "target" / "active.json"
    try {
      if (!portFile.exists) {
        forkServer(baseDirectory, portFile)
      }
      val (socket, _) = ClientSocket.socket(portFile)
      new BspClient(socket).run()
    } catch {
      case NonFatal(_) => Exit(1)
    }
  }

  /**
   * Forks another instance of sbt in the background.
   * This instance must be shutdown explicitly via `sbt -client shutdown`
   */
  def forkServer(baseDirectory: File, portfile: File): Unit = {
    val args = List("--detach-stdio")
    val launchOpts = List(
      "-Dfile.encoding=UTF-8",
      "-Dsbt.io.virtual=true",
      "-Xms1024M",
      "-Xmx1024M",
      "-Xss4M",
      "-XX:ReservedCodeCacheSize=128m"
    )

    val launcherJarString = sys.props.get("java.class.path") match {
      case Some(cp) =>
        cp.split(File.pathSeparator)
          .headOption
          .getOrElse(sys.error("launcher JAR classpath not found"))
      case _ => sys.error("property java.class.path expected")
    }

    val cmd = "java" :: launchOpts ::: "-jar" :: launcherJarString :: args
    val processBuilder =
      new ProcessBuilder(cmd: _*)
        .directory(baseDirectory)
        .redirectInput(Redirect.PIPE)

    val process = processBuilder.start()

    while (process.isAlive && !portfile.exists) Thread.sleep(100)

    if (!process.isAlive) sys.error("sbt server exited")
  }
}
