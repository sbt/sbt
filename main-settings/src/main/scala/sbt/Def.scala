/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import sbt.internal.util.Types.const
import sbt.internal.util.{ Attributed, AttributeKey, Init, ConsoleAppender }
import sbt.util.Show
import sbt.internal.util.complete.Parser
import java.io.File
import java.net.URI
import Scope.{ ThisScope, GlobalScope }
import KeyRanks.{ DTask, Invisible }

/** A concrete settings system that uses `sbt.Scope` for the scope type. */
object Def extends Init[Scope] with TaskMacroExtra {
  type Classpath = Seq[Attributed[File]]

  def settings(ss: SettingsDefinition*): Seq[Setting[_]] = ss.flatMap(_.settings)

  val triggeredBy = AttributeKey[Seq[Task[_]]]("triggered-by")
  val runBefore = AttributeKey[Seq[Task[_]]]("run-before")
  val resolvedScoped = SettingKey[ScopedKey[_]](
    "resolved-scoped",
    "The ScopedKey for the referencing setting or task.",
    KeyRanks.DSetting)
  private[sbt] val taskDefinitionKey = AttributeKey[ScopedKey[_]](
    "task-definition-key",
    "Internal: used to map a task back to its ScopedKey.",
    Invisible)

  lazy val showFullKey: Show[ScopedKey[_]] = showFullKey(None)

  def showFullKey(keyNameColor: Option[String]): Show[ScopedKey[_]] =
    Show[ScopedKey[_]]((key: ScopedKey[_]) => displayFull(key, keyNameColor))

  def showRelativeKey(
      current: ProjectRef,
      multi: Boolean,
      keyNameColor: Option[String] = None
  ): Show[ScopedKey[_]] =
    Show[ScopedKey[_]](
      key =>
        Scope.display(
          key.scope,
          withColor(key.key.label, keyNameColor),
          ref => displayRelative(current, multi, ref)
      ))

  def showBuildRelativeKey(
      currentBuild: URI,
      multi: Boolean,
      keyNameColor: Option[String] = None
  ): Show[ScopedKey[_]] =
    Show[ScopedKey[_]](
      key =>
        Scope.display(
          key.scope,
          withColor(key.key.label, keyNameColor),
          ref => displayBuildRelative(currentBuild, multi, ref)
      ))

  /**
   * Returns a String expression for the given [[Reference]] (BuildRef, [[ProjectRef]], etc)
   * relative to the current project.
   */
  def displayRelativeReference(current: ProjectRef, project: Reference): String =
    displayRelative(current, project, false)

  @deprecated("Use displayRelativeReference", "1.1.0")
  def displayRelative(current: ProjectRef, multi: Boolean, project: Reference): String =
    displayRelative(current, project, true)

  /**
   * Constructs the String of a given [[Reference]] relative to current.
   * Note that this no longer takes "multi" parameter, and omits the subproject id at all times.
   */
  private[sbt] def displayRelative(current: ProjectRef,
                                   project: Reference,
                                   trailingSlash: Boolean): String = {
    val trailing = if (trailingSlash) " /" else ""
    project match {
      case BuildRef(current.build)      => "ThisBuild" + trailing
      case `current`                    => ""
      case ProjectRef(current.build, x) => x + trailing
      case _                            => Reference.display(project) + trailing
    }
  }

  def displayBuildRelative(currentBuild: URI, multi: Boolean, project: Reference): String =
    project match {
      case BuildRef(`currentBuild`)      => "ThisBuild /"
      case ProjectRef(`currentBuild`, x) => x + " /"
      case _                             => Reference.display(project) + " /"
    }

  def displayFull(scoped: ScopedKey[_]): String = displayFull(scoped, None)

  def displayFull(scoped: ScopedKey[_], keyNameColor: Option[String]): String =
    Scope.display(scoped.scope, withColor(scoped.key.label, keyNameColor))

  def displayMasked(scoped: ScopedKey[_], mask: ScopeMask): String =
    Scope.displayMasked(scoped.scope, scoped.key.label, mask)

  def displayMasked(scoped: ScopedKey[_], mask: ScopeMask, showZeroConfig: Boolean): String =
    Scope.displayMasked(scoped.scope, scoped.key.label, mask, showZeroConfig)

  def withColor(s: String, color: Option[String]): String = {
    val useColor = ConsoleAppender.formatEnabledInEnv
    color match {
      case Some(c) if useColor => c + s + scala.Console.RESET
      case _                   => s
    }
  }

  override def deriveAllowed[T](s: Setting[T], allowDynamic: Boolean): Option[String] =
    super.deriveAllowed(s, allowDynamic) orElse
      (if (s.key.scope != ThisScope)
         Some(s"Scope cannot be defined for ${definedSettingString(s)}")
       else None) orElse
      s.dependencies
        .find(k => k.scope != ThisScope)
        .map(k =>
          s"Scope cannot be defined for dependency ${k.key.label} of ${definedSettingString(s)}")

  override def intersect(s1: Scope, s2: Scope)(
      implicit delegates: Scope => Seq[Scope]): Option[Scope] =
    if (s2 == GlobalScope) Some(s1) // s1 is more specific
    else if (s1 == GlobalScope) Some(s2) // s2 is more specific
    else super.intersect(s1, s2)

  private[this] def definedSettingString(s: Setting[_]): String =
    s"derived setting ${s.key.key.label}${positionString(s)}"
  private[this] def positionString(s: Setting[_]): String =
    s.positionString match { case None => ""; case Some(pos) => s" defined at $pos" }

  /**
   * A default Parser for splitting input into space-separated arguments.
   * `argLabel` is an optional, fixed label shown for an argument during tab completion.
   */
  def spaceDelimited(argLabel: String = "<arg>"): Parser[Seq[String]] =
    sbt.internal.util.complete.Parsers.spaceDelimited(argLabel)

  /** Lifts the result of a setting initialization into a Task. */
  def toITask[T](i: Initialize[T]): Initialize[Task[T]] = map(i)(std.TaskExtra.inlineTask)

  def toSParser[T](p: Parser[T]): State => Parser[T] = const(p)
  def toISParser[T](p: Initialize[Parser[T]]): Initialize[State => Parser[T]] = p(toSParser)
  def toIParser[T](p: Initialize[InputTask[T]]): Initialize[State => Parser[Task[T]]] = p(_.parser)

  import language.experimental.macros
  import std.TaskMacro.{
    inputTaskMacroImpl,
    inputTaskDynMacroImpl,
    taskDynMacroImpl,
    taskMacroImpl
  }
  import std.SettingMacro.{ settingDynMacroImpl, settingMacroImpl }
  import std.{ InputEvaluated, MacroPrevious, MacroValue, MacroTaskValue, ParserInput }

  def task[T](t: T): Def.Initialize[Task[T]] = macro taskMacroImpl[T]
  def taskDyn[T](t: Def.Initialize[Task[T]]): Def.Initialize[Task[T]] = macro taskDynMacroImpl[T]
  def setting[T](t: T): Def.Initialize[T] = macro settingMacroImpl[T]
  def settingDyn[T](t: Def.Initialize[T]): Def.Initialize[T] = macro settingDynMacroImpl[T]
  def inputTask[T](t: T): Def.Initialize[InputTask[T]] = macro inputTaskMacroImpl[T]
  def inputTaskDyn[T](t: Def.Initialize[Task[T]]): Def.Initialize[InputTask[T]] =
    macro inputTaskDynMacroImpl[T]

  // The following conversions enable the types Initialize[T], Initialize[Task[T]], and Task[T] to
  //  be used in task and setting macros as inputs with an ultimate result of type T

  implicit def macroValueI[T](in: Initialize[T]): MacroValue[T] = ???
  implicit def macroValueIT[T](in: Initialize[Task[T]]): MacroValue[T] = ???
  implicit def macroValueIInT[T](in: Initialize[InputTask[T]]): InputEvaluated[T] = ???
  implicit def taskMacroValueIT[T](in: Initialize[Task[T]]): MacroTaskValue[T] = ???
  implicit def macroPrevious[T](in: TaskKey[T]): MacroPrevious[T] = ???

  // The following conversions enable the types Parser[T], Initialize[Parser[T]], and Initialize[State => Parser[T]] to
  //  be used in the inputTask macro as an input with an ultimate result of type T
  implicit def parserInitToInput[T](p: Initialize[Parser[T]]): ParserInput[T] = ???
  implicit def parserInitStateToInput[T](p: Initialize[State => Parser[T]]): ParserInput[T] = ???

  def settingKey[T](description: String): SettingKey[T] = macro std.KeyMacro.settingKeyImpl[T]
  def taskKey[T](description: String): TaskKey[T] = macro std.KeyMacro.taskKeyImpl[T]
  def inputKey[T](description: String): InputKey[T] = macro std.KeyMacro.inputKeyImpl[T]

  private[sbt] def dummy[T: Manifest](name: String, description: String): (TaskKey[T], Task[T]) =
    (TaskKey[T](name, description, DTask), dummyTask(name))
  private[sbt] def dummyTask[T](name: String): Task[T] = {
    import std.TaskExtra.{ task => newTask, _ }
    val base: Task[T] = newTask(
      sys.error("Dummy task '" + name + "' did not get converted to a full task.")) named name
    base.copy(info = base.info.set(isDummyTask, true))
  }
  private[sbt] def isDummy(t: Task[_]): Boolean =
    t.info.attributes.get(isDummyTask) getOrElse false
  private[sbt] val isDummyTask = AttributeKey[Boolean](
    "is-dummy-task",
    "Internal: used to identify dummy tasks.  sbt injects values for these tasks at the start of task execution.",
    Invisible)
  private[sbt] val (stateKey, dummyState) = dummy[State]("state", "Current build state.")
  private[sbt] val (streamsManagerKey, dummyStreamsManager) = Def.dummy[std.Streams[ScopedKey[_]]](
    "streams-manager",
    "Streams manager, which provides streams for different contexts.")
}
// these need to be mixed into the sbt package object because the target doesn't involve Initialize or anything in Def
trait TaskMacroExtra {
  implicit def macroValueT[T](in: Task[T]): std.MacroValue[T] = ???
  implicit def macroValueIn[T](in: InputTask[T]): std.InputEvaluated[T] = ???
  implicit def parserToInput[T](in: Parser[T]): std.ParserInput[T] = ???
  implicit def stateParserToInput[T](in: State => Parser[T]): std.ParserInput[T] = ???
}
