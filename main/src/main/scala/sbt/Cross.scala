/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import sbt.Def.{ ScopedKey, Setting }
import sbt.Keys._
import sbt.ProjectExtra.extract
import sbt.SlashSyntax0._
import sbt.internal.Act
import sbt.internal.CommandStrings._
import sbt.internal.inc.ScalaInstance
import sbt.internal.util.AttributeKey
import sbt.internal.util.MessageOnlyException
import sbt.internal.util.complete.DefaultParsers._
import sbt.internal.util.complete.{ DefaultParsers, Parser }
import sbt.io.IO
import sbt.librarymanagement.{ SemanticSelector, VersionNumber }

/**
 * Cross implements the Scala cross building commands:
 * + ("cross") command and ++ ("switch") command.
 */
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
      val verboseOpt = Parser.opt(token(Space ~> "-v"))
      val optionalCommand = Parser.opt(token(Space ~> matched(state.combinedParser)))
      val switch1 = (token(Space ~> "-v") ~> (Space ~> version) ~ optionalCommand) map {
        case v ~ command =>
          Switch(v, true, command)
      }
      val switch2 = (spacedVersion ~ verboseOpt ~ optionalCommand) map {
        case v ~ verbose ~ command =>
          Switch(v, verbose.isDefined, command)
      }
      switch1 | switch2
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
      }
    }

  private def crossRestoreSessionParser: Parser[String] = token(CrossRestoreSessionCommand)

  private[sbt] def requireSession[T](p: State => Parser[T]): State => Parser[T] =
    s => if (s get sessionSettings isEmpty) failure("No project loaded") else p(s)

  private def resolveAggregates(extracted: Extracted): Seq[ProjectRef] = {

    def findAggregates(project: ProjectRef): Seq[ProjectRef] = {
      project :: (extracted.structure
        .allProjects(project.build)
        .find(_.id == project.project) match {
        case Some(resolved) => resolved.aggregate.toList.flatMap(findAggregates)
        case None           => Nil
      })
    }

    (extracted.currentRef +: extracted.currentProject.aggregate.flatMap(findAggregates)).distinct
  }

  private def crossVersions(extracted: Extracted, proj: ResolvedReference): Seq[String] = {
    import extracted._
    ((proj / crossScalaVersions) get structure.data) getOrElse {
      // reading scalaVersion is a one-time deal
      ((proj / scalaVersion) get structure.data).toSeq
    }
  }

  /**
   * Parse the given command into a list of aggregate projects and command to issue.
   */
  private[sbt] def parseSlashCommand(
      extracted: Extracted
  )(command: String): (Seq[ProjectRef], String) = {
    import extracted._
    import DefaultParsers._
    val parser = (OpOrID <~ charClass(_ == '/', "/")) ~ any.* map { case seg1 ~ cmd =>
      (seg1, cmd.mkString)
    }
    Parser.parse(command, parser) match {
      case Right((seg1, cmd)) =>
        structure.allProjectRefs.find(_.project == seg1) match {
          case Some(proj) => (Seq(proj), cmd)
          case _          => (resolveAggregates(extracted), command)
        }
      case _ => (resolveAggregates(extracted), command)
    }
  }

  def crossBuild: Command =
    Command.arb(requireSession(crossParser), crossHelp)(crossBuildCommandImpl)

  private def crossBuildCommandImpl(state: State, args: CrossArgs): State = {
    val extracted = Project.extract(state)
    val parser = Act.aggregatedKeyParser(extracted) ~ matched(any.*)
    val verbose = if (args.verbose) "-v" else ""
    val allCommands = Parser.parse(args.command, parser) match {
      case Left(_) =>
        val (aggs, aggCommand) = parseSlashCommand(extracted)(args.command)
        val projCrossVersions = aggs map { proj =>
          proj -> crossVersions(extracted, proj)
        }
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
              "that are configured."
          )
          state.log.debug("Scala versions configuration is:")
          projCrossVersions.foreach { case (project, versions) =>
            state.log.debug(s"$project: $versions")
          }
        }

        // Execute using a blanket switch
        projCrossVersions.toMap.apply(extracted.currentRef).flatMap { version =>
          // Force scala version
          Seq(s"$SwitchCommand $verbose $version!", aggCommand)
        }
      case Right((keys, taskArgs)) =>
        def project(key: ScopedKey[_]): Option[ProjectRef] = key.scope.project.toOption match {
          case Some(p: ProjectRef) => Some(p)
          case _                   => None
        }
        val fullArgs = if (taskArgs.trim.isEmpty) "" else s" ${taskArgs.trim}"
        val keysByVersion = keys
          .flatMap { k =>
            project(k).toSeq.flatMap(crossVersions(extracted, _).map(v => v -> k))
          }
          .groupBy(_._1)
          .mapValues(_.map(_._2).toSet)
        val commandsByVersion = keysByVersion.toSeq
          .flatMap { case (v, keys) =>
            val projects = keys.flatMap(project)
            keys.toSeq.flatMap { k =>
              project(k).filter(projects.contains).flatMap { p =>
                if (p == extracted.currentRef || !projects.contains(extracted.currentRef)) {
                  val parts =
                    project(k).map(_.project) ++ k.scope.config.toOption.map { case ConfigKey(n) =>
                      n.head.toUpper + n.tail
                    } ++ k.scope.task.toOption.map(_.label) ++ Some(k.key.label)
                  Some(v -> parts.mkString("", "/", fullArgs))
                } else None
              }
            }
          }
          .groupBy(_._1)
          .mapValues(_.map(_._2))
          .toSeq
          .sortBy(_._1)
        commandsByVersion.flatMap { case (v, commands) =>
          commands match {
            case Seq(c) => Seq(s"$SwitchCommand $verbose $v $c")
            case Seq()  => Nil // should be unreachable
            case multi if fullArgs.isEmpty =>
              Seq(s"$SwitchCommand $verbose $v all ${multi.mkString(" ")}")
            case multi => Seq(s"$SwitchCommand $verbose $v") ++ multi
          }
        }
    }
    allCommands.toList ::: CrossRestoreSessionCommand :: captureCurrentSession(state, extracted)
  }

  def crossRestoreSession: Command =
    Command.arb(_ => crossRestoreSessionParser, crossRestoreSessionHelp)((s, _) =>
      crossRestoreSessionImpl(s)
    )

  private def crossRestoreSessionImpl(state: State): State = {
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
    val (switchedState, affectedRefs) = switchScalaVersion(args, state)

    val strictCmd =
      if (args.version.force) {
        // The Scala version was forced on the whole build, run as is
        args.command
      } else
        args.command.map { rawCmd =>
          // for now, treat `all` command specially
          if (rawCmd.startsWith("all ")) rawCmd
          else {
            val (aggs, aggCommand) = parseSlashCommand(Project.extract(state))(rawCmd)
            aggs
              .intersect(affectedRefs)
              .map({ case ProjectRef(_, proj) => s"$proj/$aggCommand" })
              .mkString("all ", " ", "")
          }
        }

    strictCmd.toList ::: switchedState
  }

  private def switchScalaVersion(switch: Switch, state: State): (State, Seq[ResolvedReference]) = {
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
        included: Seq[(ResolvedReference, ScalaVersion, Seq[ScalaVersion])],
        excluded: Seq[(ResolvedReference, Seq[ScalaVersion])]
    ) = {

      instance.foreach { case (home, instance) =>
        state.log.info(s"Using Scala home $home with actual version ${instance.actualVersion}")
      }
      if (switch.version.force) {
        state.log.info(s"Forcing Scala version to $version on all projects.")
      } else {
        included
          .groupBy(_._2)
          .foreach { case (selectedVersion, projects) =>
            state.log.info(
              s"Setting Scala version to $selectedVersion on ${projects.size} projects."
            )
          }
      }
      if (excluded.nonEmpty && !switch.verbose) {
        state.log.info(s"Excluded ${excluded.size} projects, run ++ $version -v for more details.")
      }

      def detailedLog(msg: => String) =
        if (switch.verbose) state.log.info(msg) else state.log.debug(msg)

      def logProject: (ResolvedReference, Seq[ScalaVersion]) => Unit = (ref, scalaVersions) => {
        val current = if (ref == currentRef) "*" else " "
        ref match {
          case proj: ProjectRef =>
            detailedLog(s"  $current ${proj.project} ${scalaVersions.mkString("(", ", ", ")")}")
          case _ => // don't log BuildRefs
        }
      }
      detailedLog("Switching Scala version on:")
      included.foreach { case (project, _, versions) => logProject(project, versions) }
      detailedLog("Excluding projects:")
      excluded.foreach(logProject.tupled)
    }

    val projects: Seq[(ResolvedReference, Option[ScalaVersion], Seq[ScalaVersion])] = {
      val projectScalaVersions =
        structure.allProjectRefs.map(proj => proj -> crossVersions(extracted, proj))
      if (switch.version.force) {
        projectScalaVersions.map { case (ref, options) =>
          (ref, Some(version), options)
        } ++ structure.units.keys
          .map(BuildRef.apply)
          .map(proj => (proj, Some(version), crossVersions(extracted, proj)))
      } else {
        projectScalaVersions.map { case (project, scalaVersions) =>
          val selector = SemanticSelector(version)
          scalaVersions.filter(v => selector.matches(VersionNumber(v))) match {
            case Nil          => (project, None, scalaVersions)
            case Seq(version) => (project, Some(version), scalaVersions)
            case multiple =>
              sys.error(
                s"Multiple crossScalaVersions matched query '$version': ${multiple.mkString(", ")}"
              )
          }
        }
      }
    }

    val included = projects.collect { case (project, Some(version), scalaVersions) =>
      (project, version, scalaVersions)
    }
    val excluded = projects.collect { case (project, None, scalaVersions) =>
      (project, scalaVersions)
    }

    if (included.isEmpty) {
      if (isSelector(version))
        throw new MessageOnlyException(
          s"""Switch failed: no subprojects have a version matching "$version" in the crossScalaVersions setting."""
        )
      else
        throw new MessageOnlyException(
          s"""Switch failed: no subprojects list "$version" (or compatible version) in crossScalaVersions setting.
             |If you want to force it regardless, call ++ $version!""".stripMargin
        )
    }

    logSwitchInfo(included, excluded)

    (setScalaVersionsForProjects(instance, included, state, extracted), included.map(_._1))
  }

  // determine whether this is a 'specific' version or a selector
  // to be passed to SemanticSelector
  private def isSelector(version: String): Boolean =
    version.contains('*') || version.contains('x') || version.contains('X') || version.contains(
      ' '
    ) ||
      version.contains('<') || version.contains('>') || version.contains('|') || version.contains(
        '='
      )

  private def setScalaVersionsForProjects(
      instance: Option[(File, ScalaInstance)],
      projects: Seq[(ResolvedReference, String, Seq[String])],
      state: State,
      extracted: Extracted
  ): State = {
    import extracted._

    val newSettings = projects.flatMap { case (project, version, scalaVersions) =>
      val scope = Scope(Select(project), Zero, Zero, Zero)

      instance match {
        case Some((home, inst)) =>
          Seq(
            scope / scalaVersion := version,
            scope / crossScalaVersions := scalaVersions,
            scope / scalaHome := Some(home),
            scope / scalaInstance := Def.task { inst }.value,
          )
        case None =>
          Seq(
            scope / scalaVersion := version,
            scope / crossScalaVersions := scalaVersions,
            scope / scalaHome := None
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
