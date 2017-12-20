/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package test

import java.io.{ File, IOException }
import xsbt.IPC

import sbt.internal.scripted.{ StatementHandler, TestFailed }

import sbt.util.Logger
import sbt.util.Logger._

import scala.sys.process.{ BasicIO, Process }

final case class SbtInstance(process: Process, server: IPC.Server)

final class SbtHandler(directory: File,
                       launcher: File,
                       log: Logger,
                       launchOpts: Seq[String] = Seq())
    extends StatementHandler {
  type State = Option[SbtInstance]
  def initialState = None

  def apply(command: String, arguments: List[String], i: Option[SbtInstance]): Option[SbtInstance] =
    onSbtInstance(i) { (process, server) =>
      send((command :: arguments.map(escape)).mkString(" "), server)
      receive(command + " failed", server)
    }

  def onSbtInstance(i: Option[SbtInstance])(f: (Process, IPC.Server) => Unit): Option[SbtInstance] =
    i match {
      case Some(SbtInstance(_, server)) if server.isClosed =>
        finish(i)
        onNewSbtInstance(f)
      case Some(SbtInstance(process, server)) =>
        f(process, server)
        i
      case None =>
        onNewSbtInstance(f)
    }

  private[this] def onNewSbtInstance(f: (Process, IPC.Server) => Unit): Option[SbtInstance] = {
    val server = IPC.unmanagedServer
    val p = try newRemote(server)
    catch { case e: Throwable => server.close(); throw e }
    val ai = Some(SbtInstance(p, server))
    try f(p, server)
    catch {
      case e: Throwable =>
        // TODO: closing is necessary only because StatementHandler uses exceptions for signaling errors
        finish(ai); throw e
    }
    ai
  }

  def finish(state: Option[SbtInstance]) = state match {
    case Some(SbtInstance(process, server)) =>
      try {
        send("exit", server)
        process.exitValue()
      } catch {
        case _: IOException => process.destroy()
      }
    case None =>
  }
  def send(message: String, server: IPC.Server) = server.connection { _.send(message) }
  def receive(errorMessage: String, server: IPC.Server) =
    server.connection { ipc =>
      val resultMessage = ipc.receive
      if (!resultMessage.toBoolean) throw new TestFailed(errorMessage)
    }
  def newRemote(server: IPC.Server): Process = {
    val launcherJar = launcher.getAbsolutePath
    val globalBase = "-Dsbt.global.base=" + (new File(directory, "global")).getAbsolutePath
    val args = "java" :: (launchOpts.toList ++ (globalBase :: "-jar" :: launcherJar :: ("<" + server.port) :: Nil))
    val io = BasicIO(false, log).withInput(_.close())
    val p = Process(args, directory) run (io)
    val thread = new Thread() { override def run() = { p.exitValue(); server.close() } }
    thread.start()
    try { receive("Remote sbt initialization failed", server) } catch {
      case _: java.net.SocketException => throw new TestFailed("Remote sbt initialization failed")
    }
    p
  }
  import java.util.regex.Pattern.{ quote => q }
  // if the argument contains spaces, enclose it in quotes, quoting backslashes and quotes
  def escape(argument: String) =
    if (argument.contains(" "))
      "\"" + argument.replaceAll(q("""\"""), """\\""").replaceAll(q("\""), "\\\"") + "\""
    else argument
}
