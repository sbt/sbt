/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util.Attributed
import sbt.util.{ Level, Logger }

import sbt.librarymanagement.{
  Configurations,
  CrossVersion,
  Disabled,
  MavenRepository,
  ModuleID,
  Resolver
}

import java.io.File
import Configurations.Compile
import Def.Setting
import Keys._
import Scope.Global

import sbt.io.IO

object IvyConsole {
  final val Name = "ivy-console"
  lazy val command =
    Command.command(Name) { state =>
      val Dependencies(managed, repos, unmanaged) = parseDependencies(state.remainingCommands map {
        _.commandLine
      }, state.log)
      val base = new File(CommandUtil.bootDirectory(state), Name)
      IO.createDirectory(base)

      val (eval, structure) = Load.defaultLoad(state, base, state.log)
      val session = Load.initialSession(structure, eval)
      val extracted = Project.extract(session, structure)
      import extracted._

      val depSettings: Seq[Setting[_]] = Seq(
        libraryDependencies ++= managed.reverse,
        resolvers ++= repos.reverse.toVector,
        unmanagedJars in Compile ++= Attributed blankSeq unmanaged.reverse,
        logLevel in Global := Level.Warn,
        showSuccess in Global := false
      )
      val append = Load.transformSettings(Load.projectScope(currentRef),
                                          currentRef.build,
                                          rootProject,
                                          depSettings)

      val newStructure = Load.reapply(session.original ++ append, structure)
      val newState = state.copy(remainingCommands = Exec("console-quick", None) :: Nil)
      Project.setProject(session, newStructure, newState)
    }

  final case class Dependencies(managed: Seq[ModuleID],
                                resolvers: Seq[Resolver],
                                unmanaged: Seq[File])
  def parseDependencies(args: Seq[String], log: Logger): Dependencies =
    (Dependencies(Nil, Nil, Nil) /: args)(parseArgument(log))
  def parseArgument(log: Logger)(acc: Dependencies, arg: String): Dependencies =
    arg match {
      case _ if arg contains " at " => acc.copy(resolvers = parseResolver(arg) +: acc.resolvers)
      case _ if arg endsWith ".jar" => acc.copy(unmanaged = new File(arg) +: acc.unmanaged)
      case _                        => acc.copy(managed = parseManaged(arg, log) ++ acc.managed)
    }

  private[this] def parseResolver(arg: String): MavenRepository = {
    val Array(name, url) = arg.split(" at ")
    MavenRepository(name.trim, url.trim)
  }

  val DepPattern = """([^%]+)%(%?)([^%]+)%([^%]+)""".r
  def parseManaged(arg: String, log: Logger): Seq[ModuleID] =
    arg match {
      case DepPattern(group, cross, name, version) =>
        val crossV = if (cross.trim.isEmpty) Disabled() else CrossVersion.binary
        ModuleID(group.trim, name.trim, version.trim).withCrossVersion(crossV) :: Nil
      case _ => log.warn("Ignoring invalid argument '" + arg + "'"); Nil
    }
}
