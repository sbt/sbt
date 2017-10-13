/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util.{ complete, LineRange, RangePosition, Types }

import java.io.File
import java.net.URI
import Def.{ ScopedKey, Setting }
import Types.Endo
import compiler.Eval

import SessionSettings._
import sbt.internal.parser.SbtRefactorings

import sbt.io.IO

/**
 * Represents (potentially) transient settings added into a build via commands/user.
 *
 * @param currentBuild The current sbt build with which we scope new settings
 * @param currentProject The current project with which we scope new settings.
 * @param original The original list of settings for this build.
 * @param append Settings which have been defined and appended that may ALSO be saved to disk.
 * @param rawAppend Settings which have been defined and appended which CANNOT be saved to disk
 * @param currentEval A compiler we can use to compile new setting strings.
 */
final case class SessionSettings(
    currentBuild: URI,
    currentProject: Map[URI, String],
    original: Seq[Setting[_]],
    append: SessionMap,
    rawAppend: Seq[Setting[_]],
    currentEval: () => Eval
) {

  assert(currentProject contains currentBuild,
         s"Current build ($currentBuild) not associated with a current project.")

  /**
   * Modifiy the current state.
   *
   * @param build  The buid with which we scope new settings.
   * @param project The project reference with which we scope new settings.
   * @param eval  The mechanism to compile new settings.
   * @return  A new SessionSettings object
   */
  def setCurrent(build: URI, project: String, eval: () => Eval): SessionSettings =
    copy(currentBuild = build,
         currentProject = currentProject.updated(build, project),
         currentEval = eval)

  /**
   * @return  The current ProjectRef with which we scope settings.
   */
  def current: ProjectRef = ProjectRef(currentBuild, currentProject(currentBuild))

  /**
   * Appends a set of settings which can be persisted to disk
   * @param s  A sequence of SessionSetting objects, which contain a Setting[_] and a string.
   * @return  A new SessionSettings which contains this new sequence.
   */
  def appendSettings(s: Seq[SessionSetting]): SessionSettings =
    copy(append = modify(append, _ ++ s))

  /**
   * Appends a set of raw Setting[_] objects to the current session.
   * @param ss  The raw settings to include
   * @return A new SessionSettings with the appended settings.
   */
  def appendRaw(ss: Seq[Setting[_]]): SessionSettings = copy(rawAppend = rawAppend ++ ss)

  /**
   * @return  A combined list of all Setting[_] objects for the current session, in priority order.
   */
  def mergeSettings: Seq[Setting[_]] = original ++ merge(append) ++ rawAppend

  /**
   * @return  A new SessionSettings object where additional transient settings are removed.
   */
  def clearExtraSettings: SessionSettings = copy(append = Map.empty, rawAppend = Nil)

  private[this] def merge(map: SessionMap): Seq[Setting[_]] =
    map.values.toSeq.flatten[SessionSetting].map(_._1)

  private[this] def modify(map: SessionMap, onSeq: Endo[Seq[SessionSetting]]): SessionMap = {
    val cur = current
    map.updated(cur, onSeq(map.getOrElse(cur, Nil)))
  }
}

object SessionSettings {

  /** A session setting is simply a tuple of a Setting[_] and the strings which define it. */
  type SessionSetting = (Setting[_], Seq[String])

  type SessionMap = Map[ProjectRef, Seq[SessionSetting]]
  type SbtConfigFile = (File, Seq[String])

  /**
   * This will re-evaluate all Setting[_]'s on this session against the current build state and
   * return the new build state.
   */
  def reapply(session: SessionSettings, s: State): State =
    BuiltinCommands.reapply(session, Project.structure(s), s)

  /**
   * This will clear any user-added session settings for a given build state and return the new build state.
   *
   * Note: Does not clear `rawAppend` settings
   */
  def clearSettings(s: State): State =
    withSettings(s)(session => reapply(session.copy(append = session.append - session.current), s))

  /** This will clear ALL transient session settings in a given build state, returning the new build state. */
  def clearAllSettings(s: State): State =
    withSettings(s)(session => reapply(session.clearExtraSettings, s))

  /**
   * A convenience method to alter the current build state using the current SessionSettings.
   *
   * @param s  The current build state
   * @param f  A function which takes the current SessionSettings and returns the new build state.
   * @return The new build state
   */
  def withSettings(s: State)(f: SessionSettings => State): State = {
    val extracted = Project extract s
    import extracted._
    if (session.append.isEmpty) {
      s.log.info("No session settings defined.")
      s
    } else
      f(session)
  }

  /** Adds `s` to a strings when needed.    Maybe one day we'll care about non-english languages. */
  def pluralize(size: Int, of: String) = size.toString + (if (size == 1) of else (of + "s"))

  /** Checks to see if any session settings are being discarded and issues a warning. */
  def checkSession(newSession: SessionSettings, oldState: State): Unit = {
    val oldSettings = (oldState get Keys.sessionSettings).toList.flatMap(_.append).flatMap(_._2)
    if (newSession.append.isEmpty && oldSettings.nonEmpty)
      oldState.log.warn(
        "Discarding " + pluralize(oldSettings.size, " session setting") + ".  Use 'session save' to persist session settings.")
  }

  def removeRanges[T](in: Seq[T], ranges: Seq[(Int, Int)]): Seq[T] = {
    val asSet = (Set.empty[Int] /: ranges) { case (s, (hi, lo)) => s ++ (hi to lo) }
    in.zipWithIndex.flatMap { case (t, index) => if (asSet(index + 1)) Nil else t :: Nil }
  }

  /**
   * Removes settings from the current session, by range.
   * @param s The current build state.
   * @param ranges A set of Low->High tuples for which settings to remove.
   * @return  The new build state with settings removed.
   */
  def removeSettings(s: State, ranges: Seq[(Int, Int)]): State =
    withSettings(s) { session =>
      val current = session.current
      val newAppend = session.append
        .updated(current, removeRanges(session.append.getOrElse(current, Nil), ranges))
      reapply(session.copy(append = newAppend), s)
    }

  /** Saves *all* session settings to disk for all projects. */
  def saveAllSettings(s: State): State = saveSomeSettings(s)(_ => true)

  /** Saves the session settings to disk for the current project. */
  def saveSettings(s: State): State = {
    val current = Project.session(s).current
    saveSomeSettings(s)(_ == current)
  }

  /**
   * Saves session settings to disk if they match the filter.
   * @param s  The build state
   * @param include  A filter function to determine which project's settings to persist.
   * @return  The new build state.
   */
  def saveSomeSettings(s: State)(include: ProjectRef => Boolean): State =
    withSettings(s) { session =>
      val newSettings =
        for ((ref, settings) <- session.append if settings.nonEmpty && include(ref)) yield {
          val (news, olds) =
            writeSettings(ref, settings.toList, session.original, Project.structure(s))
          (ref -> news, olds)
        }
      val (newAppend, newOriginal) = newSettings.unzip
      val newSession = session.copy(append = newAppend.toMap, original = newOriginal.flatten.toSeq)
      reapply(newSession.copy(original = newSession.mergeSettings, append = Map.empty), s)
    }

  def writeSettings(pref: ProjectRef,
                    settings: List[SessionSetting],
                    original: Seq[Setting[_]],
                    structure: BuildStructure): (Seq[SessionSetting], Seq[Setting[_]]) = {
    val project =
      Project.getProject(pref, structure).getOrElse(sys.error("Invalid project reference " + pref))
    val writeTo: File = BuildPaths
      .configurationSources(project.base)
      .headOption
      .getOrElse(new File(project.base, "build.sbt"))
    writeTo.createNewFile()

    val path = writeTo.getAbsolutePath
    val (inFile, other, _) =
      ((List[Setting[_]](), List[Setting[_]](), Set.empty[ScopedKey[_]]) /: original.reverse) {
        case ((in, oth, keys), s) =>
          s.pos match {
            case RangePosition(`path`, _) if !keys.contains(s.key) => (s :: in, oth, keys + s.key)
            case _                                                 => (in, s :: oth, keys)
          }
      }

    val (_, oldShifted, replace) = ((0, List[Setting[_]](), Seq[SessionSetting]()) /: inFile) {
      case ((offs, olds, repl), s) =>
        val RangePosition(_, r @ LineRange(start, end)) = s.pos
        settings find (_._1.key == s.key) match {
          case Some(ss @ (ns, newLines)) if !ns.init.dependencies.contains(ns.key) =>
            val shifted = ns withPos RangePosition(path,
                                                   LineRange(start - offs,
                                                             start - offs + newLines.size))
            (offs + end - start - newLines.size, shifted :: olds, ss +: repl)
          case _ =>
            val shifted = s withPos RangePosition(path, r shift -offs)
            (offs, shifted :: olds, repl)
        }
    }
    val newSettings = settings diff replace
    val oldContent = IO.readLines(writeTo)
    val (_, exist) = SbtRefactorings.applySessionSettings((writeTo, oldContent), replace)
    val adjusted = if (newSettings.nonEmpty && needsTrailingBlank(exist)) exist :+ "" else exist
    val lines = adjusted ++ newSettings.flatMap(x => x._2 :+ "")
    IO.writeLines(writeTo, lines)
    val (newWithPos, _) = ((List[SessionSetting](), adjusted.size + 1) /: newSettings) {
      case ((acc, line), (s, newLines)) =>
        val endLine = line + newLines.size
        ((s withPos RangePosition(path, LineRange(line, endLine)), newLines) :: acc, endLine + 1)
    }
    (newWithPos.reverse, other ++ oldShifted)
  }

  def needsTrailingBlank(lines: Seq[String]) =
    lines.nonEmpty && !lines.takeRight(1).exists(_.trim.isEmpty)

  /** Prints all the user-defined SessionSettings (not raw) to System.out. */
  def printAllSettings(s: State): State =
    withSettings(s) { session =>
      for ((ref, settings) <- session.append if settings.nonEmpty) {
        println("In " + Reference.display(ref))
        printSettings(settings)
      }
      s
    }

  /** Prints all the defined session settings for the current project in the given build state. */
  def printSettings(s: State): State =
    withSettings(s) { session =>
      printSettings(session.append.getOrElse(session.current, Nil))
      s
    }

  /** Prints all the passed in session settings */
  def printSettings(settings: Seq[SessionSetting]): Unit =
    for (((_, stringRep), index) <- settings.zipWithIndex)
      println("  " + (index + 1) + ". " + stringRep.mkString("\n"))

  def Help =
    """session <command>

Manipulates session settings, which are temporary settings that do not persist past the current sbt execution (that is, the current session).
Valid commands are:

clear, clear-all

	Removes temporary settings added using 'set' and re-evaluates all settings.
	For 'clear', only the settings defined for the current project are cleared.
	For 'clear-all', all settings in all projects are cleared.

list, list-all

	Prints a numbered list of session settings defined.
	The numbers may be used to remove individual settings or ranges of settings using 'remove'.
	For 'list', only the settings for the current project are printed.
	For 'list-all', all settings in all projets are printed.

remove <range-spec>

	<range-spec> is a comma-separated list of individual numbers or ranges of numbers.
	For example, 'remove 1,3,5-7'.
	The temporary settings at the given indices for the current project are removed and all settings are re-evaluated.
	Use the 'list' command to see a numbered list of settings for the current project.

save, save-all

	Makes the session settings permanent by writing them to a '.sbt' configuration file.
	For 'save', only the current project's settings are saved (the settings for other projects are left alone).
	For 'save-all', the session settings are saved for all projects.
	The session settings defined for a project are appended to the first '.sbt' configuration file in that project.
	If no '.sbt' configuration file exists, the settings are written to 'build.sbt' in the project's base directory."""

  /** AST for the syntax of the session command.  Each subclass is an action that can be performed. */
  sealed trait SessionCommand

  final class Print(val all: Boolean) extends SessionCommand

  final class Clear(val all: Boolean) extends SessionCommand

  final class Save(val all: Boolean) extends SessionCommand

  final class Remove(val ranges: Seq[(Int, Int)]) extends SessionCommand

  import complete._
  import DefaultParsers._

  /** Parser for the session command. */
  lazy val parser =
    token(Space) ~>
      (token("list-all" ^^^ new Print(true)) | token("list" ^^^ new Print(false)) | token(
        "clear" ^^^ new Clear(false)) |
        token("save-all" ^^^ new Save(true)) | token("save" ^^^ new Save(false)) | token(
        "clear-all" ^^^ new Clear(true)) |
        remove)

  lazy val remove = token("remove") ~> token(Space) ~> natSelect.map(ranges => new Remove(ranges))

  def natSelect = rep1sep(token(range, "<range>"), ',')

  def range: Parser[(Int, Int)] = (NatBasic ~ ('-' ~> NatBasic).?).map {
    case lo ~ hi => (lo, hi getOrElse lo)
  }

  /** The raw implementation of the session command. */
  def command(s: State): Parser[() => State] = Command.applyEffect(parser) {
    case p: Print  => if (p.all) printAllSettings(s) else printSettings(s)
    case v: Save   => if (v.all) saveAllSettings(s) else saveSettings(s)
    case c: Clear  => if (c.all) clearAllSettings(s) else clearSettings(s)
    case r: Remove => removeSettings(s, r.ranges)
  }
}
