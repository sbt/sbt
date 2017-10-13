/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.io.File

import sbt.Def.{ ScopedKey, Setting }
import sbt.Keys._
import sbt.internal.Act
import sbt.internal.CommandStrings._
import sbt.internal.inc.ScalaInstance
import sbt.internal.util.AttributeKey
import sbt.internal.util.complete.DefaultParsers._
import sbt.internal.util.complete.{ DefaultParsers, Parser }
import sbt.io.IO
import sbt.librarymanagement.CrossVersion

object Cross {

  private[sbt] def spacedFirst(name: String) = opOrIDSpaced(name) ~ any.+

  private case class Switch(version: ScalaVersion, verbose: Boolean, command: Option[String])
  private trait ScalaVersion {
    def force: Boolean
  }
  private case class NamedScalaVersion(name: String, force: Boolean) extends ScalaVersion
  private case class ScalaHomeVersion(home: File, resolveVersion: Option[String], force: Boolean)
      extends ScalaVersion

  private def switchParser(state: State): Parser[Switch] = {
    import DefaultParsers._
    def versionAndCommand(spacePresent: Boolean) = {
      val x = Project.extract(state)
      import x._
      val knownVersions = crossVersions(x, currentRef)
      val version = token(StringBasic.examples(knownVersions: _*)).map { arg =>
        val force = arg.endsWith("!")
        val versionArg = if (force) arg.dropRight(1) else arg
        versionArg.split("=", 2) match {
          case Array(home) if new File(home).exists() =>
            ScalaHomeVersion(new File(home), None, force)
          case Array(v) => NamedScalaVersion(v, force)
          case Array(v, home) =>
            ScalaHomeVersion(new File(home), Some(v).filterNot(_.isEmpty), force)
        }
      }
      val spacedVersion = if (spacePresent) version else version & spacedFirst(SwitchCommand)
      val verbose = Parser.opt(token(Space ~> "-v"))
      val optionalCommand = Parser.opt(token(Space ~> matched(state.combinedParser)))
      (spacedVersion ~ verbose ~ optionalCommand).map {
        case v ~ verbose ~ command =>
          Switch(v, verbose.isDefined, command)
      }
    }

    token(SwitchCommand ~> OptSpace) flatMap { sp =>
      versionAndCommand(sp.nonEmpty)
    }
  }

  private case class CrossArgs(command: String, verbose: Boolean)

  private def crossParser(state: State): Parser[CrossArgs] =
    token(CrossCommand <~ OptSpace) flatMap { _ =>
      (token(Parser.opt("-v" <~ Space)) ~ token(matched(state.combinedParser))).map {
        case (verbose, command) => CrossArgs(command, verbose.isDefined)
      } & spacedFirst(CrossCommand)
    }

  private def crossRestoreSessionParser(state: State): Parser[String] =
    token(CrossRestoreSessionCommand)

  private[sbt] def requireSession[T](p: State => Parser[T]): State => Parser[T] =
    s => if (s get sessionSettings isEmpty) failure("No project loaded") else p(s)

  private def resolveAggregates(extracted: Extracted): Seq[ProjectRef] = {
    import extracted._

    def findAggregates(project: ProjectRef): List[ProjectRef] = {
      project :: (structure.allProjects(project.build).find(_.id == project.project) match {
        case Some(resolved) => resolved.aggregate.toList.flatMap(findAggregates)
        case None           => Nil
      })
    }

    (currentRef :: currentProject.aggregate.toList.flatMap(findAggregates)).distinct
  }

  private def crossVersions(extracted: Extracted, proj: ResolvedReference): Seq[String] = {
    import extracted._
    (crossScalaVersions in proj get structure.data) getOrElse {
      // reading scalaVersion is a one-time deal
      (scalaVersion in proj get structure.data).toSeq
    }
  }

  /**
   * Parse the given command into either an aggregate command or a command for a project
   */
  private def parseCommand(command: String): Either[String, (String, String)] = {
    import DefaultParsers._
    val parser = (OpOrID <~ charClass(_ == '/', "/")) ~ any.* map {
      case project ~ cmd => (project, cmd.mkString)
    }
    Parser.parse(command, parser).left.map(_ => command)
  }

  def crossBuild: Command =
    Command.arb(requireSession(crossParser), crossHelp)(crossBuildCommandImpl)

  private def crossBuildCommandImpl(state: State, args: CrossArgs): State = {
    val x = Project.extract(state)
    import x._

    val (aggs, aggCommand) = parseCommand(args.command) match {
      case Right((project, cmd)) =>
        (structure.allProjectRefs.filter(_.project == project), cmd)
      case Left(cmd) => (resolveAggregates(x), cmd)
    }

    val projCrossVersions = aggs map { proj =>
      proj -> crossVersions(x, proj)
    }
    // if we support scalaVersion, projVersions should be cached somewhere since
    // running ++2.11.1 is at the root level is going to mess with the scalaVersion for the aggregated subproj
    val projVersions = (projCrossVersions flatMap {
      case (proj, versions) => versions map { proj.project -> _ }
    }).toList

    val verbose = if (args.verbose) "-v" else ""

    if (projVersions.isEmpty) {
      state
    } else {
      // Detect whether a task or command has been issued
      val allCommands = Parser.parse(aggCommand, Act.aggregatedKeyParser(x)) match {
        case Left(_) =>
          // It's definitely not a task, check if it's a valid command, because we don't want to emit the warning
          // message below for typos.
          val validCommand = Parser.parse(aggCommand, state.combinedParser).isRight

          val distinctCrossConfigs = projCrossVersions.map(_._2.toSet).distinct
          if (validCommand && distinctCrossConfigs.size > 1) {
            state.log.warn(
              "Issuing a cross building command, but not all sub projects have the same cross build " +
                "configuration. This could result in subprojects cross building against Scala versions that they are " +
                "not compatible with. Try issuing cross building command with tasks instead, since sbt will be able " +
                "to ensure that cross building is only done using configured project and Scala version combinations " +
                "that are configured.")
            state.log.debug("Scala versions configuration is:")
            projCrossVersions.foreach {
              case (project, versions) => state.log.debug(s"$project: $versions")
            }
          }

          // Execute using a blanket switch
          projCrossVersions.toMap.apply(currentRef).flatMap { version =>
            // Force scala version
            Seq(s"$SwitchCommand $verbose $version!", aggCommand)
          }

        case Right(_) =>
          // We have a key, we're likely to be able to cross build this using the per project behaviour.

          // Group all the projects by scala version
          projVersions.groupBy(_._2).mapValues(_.map(_._1)).toSeq.flatMap {
            case (version, Seq(project)) =>
              // If only one project for a version, issue it directly
              Seq(s"$SwitchCommand $verbose $version $project/$aggCommand")
            case (version, projects) if aggCommand.contains(" ") =>
              // If the command contains a space, then the `all` command won't work because it doesn't support issuing
              // commands with spaces, so revert to running the command on each project one at a time
              s"$SwitchCommand $verbose $version" :: projects.map(project =>
                s"$project/$aggCommand")
            case (version, projects) =>
              // First switch scala version, then use the all command to run the command on each project concurrently
              Seq(s"$SwitchCommand $verbose $version",
                  projects.map(_ + "/" + aggCommand).mkString("all ", " ", ""))
          }
      }

      allCommands.toList ::: CrossRestoreSessionCommand :: captureCurrentSession(state, x)
    }
  }

  def crossRestoreSession: Command =
    Command.arb(crossRestoreSessionParser, crossRestoreSessionHelp)(crossRestoreSessionImpl)

  private def crossRestoreSessionImpl(state: State, arg: String): State = {
    restoreCapturedSession(state, Project.extract(state))
  }

  private val CapturedSession = AttributeKey[Seq[Setting[_]]]("crossCapturedSession")

  private def captureCurrentSession(state: State, extracted: Extracted): State = {
    state.put(CapturedSession, extracted.session.rawAppend)
  }

  private def restoreCapturedSession(state: State, extracted: Extracted): State = {
    state.get(CapturedSession) match {
      case Some(rawAppend) =>
        val restoredSession = extracted.session.copy(rawAppend = rawAppend)
        BuiltinCommands
          .reapply(restoredSession, extracted.structure, state)
          .remove(CapturedSession)
      case None => state
    }
  }

  def switchVersion: Command =
    Command.arb(requireSession(switchParser), switchHelp)(switchCommandImpl)

  private def switchCommandImpl(state: State, args: Switch): State = {
    val switchedState = switchScalaVersion(args, state)

    args.command.toList ::: switchedState
  }

  private def switchScalaVersion(switch: Switch, state: State): State = {
    val extracted = Project.extract(state)
    import extracted._

    type ScalaVersion = String

    val (version, instance) = switch.version match {
      case ScalaHomeVersion(homePath, resolveVersion, _) =>
        val home = IO.resolve(extracted.currentProject.base, homePath)
        if (home.exists()) {
          val instance = ScalaInstance(home)(state.classLoaderCache.apply _)
          val version = resolveVersion.getOrElse(instance.actualVersion)
          (version, Some((home, instance)))
        } else {
          sys.error(s"Scala home directory did not exist: $home")
        }
      case NamedScalaVersion(v, _) => (v, None)
    }

    def logSwitchInfo(
        included: Seq[(ProjectRef, Seq[ScalaVersion])],
        excluded: Seq[(ProjectRef, Seq[ScalaVersion])]
    ) = {

      instance.foreach {
        case (home, instance) =>
          state.log.info(s"Using Scala home $home with actual version ${instance.actualVersion}")
      }
      if (switch.version.force) {
        state.log.info(s"Forcing Scala version to $version on all projects.")
      } else {
        state.log.info(s"Setting Scala version to $version on ${included.size} projects.")
      }
      if (excluded.nonEmpty && !switch.verbose) {
        state.log.info(s"Excluded ${excluded.size} projects, run ++ $version -v for more details.")
      }

      def detailedLog(msg: => String) =
        if (switch.verbose) state.log.info(msg) else state.log.debug(msg)

      def logProject: (ProjectRef, Seq[ScalaVersion]) => Unit = (proj, scalaVersions) => {
        val current = if (proj == currentRef) "*" else " "
        detailedLog(s"  $current ${proj.project} ${scalaVersions.mkString("(", ", ", ")")}")
      }
      detailedLog("Switching Scala version on:")
      included.foreach(logProject.tupled)
      detailedLog("Excluding projects:")
      excluded.foreach(logProject.tupled)
    }

    val projects: Seq[(ResolvedReference, Seq[ScalaVersion])] = {
      val projectScalaVersions =
        structure.allProjectRefs.map(proj => proj -> crossVersions(extracted, proj))
      if (switch.version.force) {
        logSwitchInfo(projectScalaVersions, Nil)
        projectScalaVersions ++ structure.units.keys
          .map(BuildRef.apply)
          .map(proj => proj -> crossVersions(extracted, proj))
      } else {
        val binaryVersion = CrossVersion.binaryScalaVersion(version)

        val (included, excluded) = projectScalaVersions.partition {
          case (_, scalaVersions) =>
            scalaVersions.exists(v => CrossVersion.binaryScalaVersion(v) == binaryVersion)
        }
        logSwitchInfo(included, excluded)
        included
      }
    }

    setScalaVersionForProjects(version, instance, projects, state, extracted)
  }

  private def setScalaVersionForProjects(
      version: String,
      instance: Option[(File, ScalaInstance)],
      projects: Seq[(ResolvedReference, Seq[String])],
      state: State,
      extracted: Extracted
  ): State = {
    import extracted._

    val newSettings = projects.flatMap {
      case (project, scalaVersions) =>
        val scope = Scope(Select(project), Zero, Zero, Zero)

        instance match {
          case Some((home, inst)) =>
            Seq(
              scalaVersion in scope := version,
              crossScalaVersions in scope := scalaVersions,
              scalaHome in scope := Some(home),
              scalaInstance in scope := inst
            )
          case None =>
            Seq(
              scalaVersion in scope := version,
              crossScalaVersions in scope := scalaVersions,
              scalaHome in scope := None
            )
        }
    }

    val filterKeys: Set[AttributeKey[_]] = Set(scalaVersion, scalaHome, scalaInstance).map(_.key)

    val projectsContains: Reference => Boolean = projects.map(_._1).toSet.contains

    // Filter out any old scala version settings that were added, this is just for hygiene.
    val filteredRawAppend = session.rawAppend.filter(_.key match {
      case ScopedKey(Scope(Select(ref), Zero, Zero, Zero), key)
          if filterKeys.contains(key) && projectsContains(ref) =>
        false
      case _ => true
    })

    val newSession = session.copy(rawAppend = filteredRawAppend ++ newSettings)

    BuiltinCommands.reapply(newSession, structure, state)
  }

}
