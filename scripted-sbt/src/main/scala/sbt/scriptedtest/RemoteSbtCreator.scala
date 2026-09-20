/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package scriptedtest

import xsbt.IPC

import scala.sys.process.{ BasicIO, Process }

private[sbt] sealed trait RemoteSbtCreatorProp
private[sbt] object RemoteSbtCreatorProp:
  case class LauncherBased(launcherJar: File) extends RemoteSbtCreatorProp
  case class RunFromSourceBased(scalaVersion: String, sbtVersion: String, classpath: Seq[File])
      extends RemoteSbtCreatorProp

abstract class RemoteSbtCreator private[sbt]:
  def newRemote(server: IPC.Server): Process

private[sbt] object RemoteSbtCreator:

  /**
   * The publishing targets scripted gives the forked sbt, as the system property that points at
   * each one and its directory name. They sit under `global/`, which survives the wipe between
   * the tests of a batch, so `ScriptedTests` empties them itself via `isolatedRepos`.
   */
  private val isolatedRepoProps =
    List("-Dsbt.local.repository=" -> "local-repo", "-Dmaven.repo.local=" -> "m2-repo")

  private def globalDir(testDirectory: File): File = new File(testDirectory, "global")

  /** The directories `isolationProps` points the forked sbt at, for the caller to empty. */
  def isolatedRepos(testDirectory: File): List[File] =
    isolatedRepoProps.map((_, name) => new File(globalDir(testDirectory), name))

  /**
   * Gives the forked sbt a publishing target of its own, under the directory scripted already
   * throws away, so a test cannot write into the developer's `~/.ivy2/local` or `~/.m2`.
   *
   * Only the publish targets move. The shared download caches are left alone, so tests do not
   * re-resolve everything from the network, and the real local repositories stay readable. A
   * build that wants the old behavior back sets the property itself in `scriptedLaunchOpts`.
   */
  def isolationProps(testDirectory: File, launchOpts: Seq[String]): List[String] =
    isolatedRepoProps.flatMap: (prefix, name) =>
      if launchOpts.exists(_.startsWith(prefix)) then Nil
      else List(prefix + new File(globalDir(testDirectory), name).getAbsolutePath)
end RemoteSbtCreator

final class LauncherBasedRemoteSbtCreator(
    directory: File,
    launcher: File,
    log: Logger,
    javaCommand: String,
    launchOpts: Seq[String],
) extends RemoteSbtCreator:
  def this(
      directory: File,
      launcher: File,
      log: Logger,
      launchOpts: Seq[String] = Nil,
  ) = this(directory, launcher, log, "java", launchOpts)

  def newRemote(server: IPC.Server): Process =
    val launcherJar = launcher.getAbsolutePath
    val globalBase = "-Dsbt.global.base=" + (new File(directory, "global")).getAbsolutePath
    val scripted = "-Dsbt.scripted=true"
    val args = List("<" + server.port)
    val isolation = RemoteSbtCreator.isolationProps(directory, launchOpts)
    val cmd =
      javaCommand :: launchOpts.toList ::: isolation ::: globalBase :: scripted :: "-jar" :: launcherJar :: args ::: Nil
    val io = BasicIO(false, log).withInput(_.close())
    val p = Process(cmd, directory).run(io)
    val thread = new Thread():
      override def run(): Unit =
        p.exitValue(); server.close()
    thread.start()
    p
end LauncherBasedRemoteSbtCreator

final class RunFromSourceBasedRemoteSbtCreator(
    directory: File,
    log: Logger,
    javaCommand: String,
    launchOpts: Seq[String],
    scalaVersion: String,
    sbtVersion: String,
    classpath: Seq[File],
) extends RemoteSbtCreator:
  def this(
      directory: File,
      log: Logger,
      launchOpts: Seq[String] = Nil,
      scalaVersion: String,
      sbtVersion: String,
      classpath: Seq[File],
  ) = this(directory, log, "java", launchOpts, scalaVersion, sbtVersion, classpath)

  def newRemote(server: IPC.Server): Process =
    val globalBase = "-Dsbt.global.base=" + new File(directory, "global").getAbsolutePath
    val scripted = "-Dsbt.scripted=true"
    val mainClassName = "sbt.RunFromSourceMain"
    val cpString = classpath.mkString(java.io.File.pathSeparator)
    val args =
      List(mainClassName, directory.toString, scalaVersion, sbtVersion, cpString, "<" + server.port)
    val isolation = RemoteSbtCreator.isolationProps(directory, launchOpts)
    val cmd =
      javaCommand :: launchOpts.toList ::: isolation ::: globalBase :: scripted :: "-cp" :: cpString :: args ::: Nil
    val io = BasicIO(false, log).withInput(_.close())
    val p = Process(cmd, directory) run (io)
    val thread = new Thread():
      override def run() =
        p.exitValue(); server.close()
    thread.start()
    p
end RunFromSourceBasedRemoteSbtCreator
