/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.client

import java.io.{ InputStream, OutputStream }
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

import sbt.Exit
import scala.util.control.NonFatal

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
    Exit(NetworkClient.run(configuration, configuration.arguments.toList, redirectOutput = true))
  }
}
