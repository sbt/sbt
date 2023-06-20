/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package scriptedtest

import java.io.IOException
import java.net.SocketException

import scala.sys.process.Process

import sbt.internal.scripted.{ StatementHandler, TestFailed }
import sbt.internal.util.RunningProcesses

import xsbt.IPC

final case class SbtInstance(process: Process, server: IPC.Server)

final class SbtHandler(remoteSbtCreator: RemoteSbtCreator) extends StatementHandler {
  Signals.register(() => RunningProcesses.killAll())

  type State = Option[SbtInstance]

  def initialState = None

  def apply(command: String, arguments: List[String], i: Option[SbtInstance]): Option[SbtInstance] =
    onSbtInstance(i) { (_, server) =>
      send((command :: arguments).mkString(" "), server)
      receive(s"$command failed", server)
    }

  def onSbtInstance(i: Option[SbtInstance])(f: (Process, IPC.Server) => Unit): Option[SbtInstance] =
    i match {
      case Some(SbtInstance(_, server)) if server.isClosed => finish(i); onNewSbtInstance(f)
      case Some(SbtInstance(process, server))              => f(process, server); i
      case None                                            => onNewSbtInstance(f)
    }

  private[this] def onNewSbtInstance(f: (Process, IPC.Server) => Unit): Option[SbtInstance] = {
    val server = IPC.unmanagedServer
    val p =
      try newRemote(server)
      catch { case e: Throwable => server.close(); throw e }
    val i = Some(SbtInstance(p, server))
    try f(p, server)
    catch {
      case e: Throwable =>
        // TODO: closing is necessary only because StatementHandler uses exceptions for signaling errors
        finish(i)
        throw e
    }
    i
  }

  def finish(state: State) = state match {
    case None =>
    case Some(SbtInstance(process, server)) =>
      try {
        send("exit", server)
        process.exitValue()
        ()
      } catch {
        case _: IOException => process.destroy()
      } finally {
        if (process.isAlive) process.destroy()
        RunningProcesses.remove(process)
      }
  }

  def send(message: String, server: IPC.Server) = server.connection(_.send(message))

  def receive(errorMessage: String, server: IPC.Server) =
    server.connection { ipc =>
      val resultMessage = ipc.receive
      if (!resultMessage.toBoolean) throw new TestFailed(errorMessage)
    }

  def newRemote(server: IPC.Server): Process = {
    val p = remoteSbtCreator.newRemote(server)
    RunningProcesses.add(p)
    try receive("Remote sbt initialization failed", server)
    catch { case _: SocketException => throw new TestFailed("Remote sbt initialization failed") }
    p
  }
}
