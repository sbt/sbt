/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package scriptedtest

import java.io.File

import scala.sys.process.{ BasicIO, Process }

import sbt.io.IO
import sbt.util.Logger

import xsbt.IPC

private[sbt] sealed trait RemoteSbtCreatorKind
private[sbt] object RemoteSbtCreatorKind {
  case object LauncherBased extends RemoteSbtCreatorKind
  case object RunFromSourceBased extends RemoteSbtCreatorKind
}

abstract class RemoteSbtCreator private[sbt] {
  def newRemote(server: IPC.Server): Process
}

final class LauncherBasedRemoteSbtCreator(
    directory: File,
    launcher: File,
    log: Logger,
    launchOpts: Seq[String] = Nil,
) extends RemoteSbtCreator {
  def newRemote(server: IPC.Server) = {
    val launcherJar = launcher.getAbsolutePath
    val globalBase = "-Dsbt.global.base=" + (new File(directory, "global")).getAbsolutePath
    val args = List("<" + server.port)
    val cmd = "java" :: launchOpts.toList ::: globalBase :: "-jar" :: launcherJar :: args ::: Nil
    val io = BasicIO(false, log).withInput(_.close())
    val p = Process(cmd, directory) run (io)
    val thread = new Thread() { override def run() = { p.exitValue(); server.close() } }
    thread.start()
    p
  }
}

final class RunFromSourceBasedRemoteSbtCreator(
    directory: File,
    log: Logger,
    launchOpts: Seq[String] = Nil,
) extends RemoteSbtCreator {
  def newRemote(server: IPC.Server) = {
    val globalBase = "-Dsbt.global.base=" + (new File(directory, "global")).getAbsolutePath
    val cp = IO readLinesURL (getClass getResource "/RunFromSource.classpath")
    val cpString = cp mkString File.pathSeparator
    val mainClassName = "sbt.RunFromSourceMain"
    val args = List(mainClassName, directory.toString, "<" + server.port)
    val cmd = "java" :: launchOpts.toList ::: globalBase :: "-cp" :: cpString :: args ::: Nil
    val io = BasicIO(false, log).withInput(_.close())
    val p = Process(cmd, directory) run (io)
    val thread = new Thread() { override def run() = { p.exitValue(); server.close() } }
    thread.start()
    p
  }
}
