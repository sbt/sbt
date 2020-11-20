/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package scriptedtest

import xsbt.IPC

import scala.sys.process.{ BasicIO, Process }

private[sbt] sealed trait RemoteSbtCreatorProp
private[sbt] object RemoteSbtCreatorProp {
  case class LauncherBased(launcherJar: File) extends RemoteSbtCreatorProp
  case class RunFromSourceBased(scalaVersion: String, sbtVersion: String, classpath: Seq[File])
      extends RemoteSbtCreatorProp
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
    val scripted = "-Dsbt.scripted=true"
    val args = List("<" + server.port)
    val cmd = "java" :: launchOpts.toList ::: globalBase :: scripted :: "-jar" :: launcherJar :: args ::: Nil
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
    scalaVersion: String,
    sbtVersion: String,
    classpath: Seq[File],
) extends RemoteSbtCreator {
  def newRemote(server: IPC.Server): Process = {
    val globalBase = "-Dsbt.global.base=" + new File(directory, "global").getAbsolutePath
    val scripted = "-Dsbt.scripted=true"
    val mainClassName = "sbt.RunFromSourceMain"
    val cpString = classpath.mkString(java.io.File.pathSeparator)
    val args =
      List(mainClassName, directory.toString, scalaVersion, sbtVersion, cpString, "<" + server.port)
    val cmd = "java" :: launchOpts.toList ::: globalBase :: scripted :: "-cp" :: cpString :: args ::: Nil
    val io = BasicIO(false, log).withInput(_.close())
    val p = Process(cmd, directory) run (io)
    val thread = new Thread() { override def run() = { p.exitValue(); server.close() } }
    thread.start()
    p
  }
}
