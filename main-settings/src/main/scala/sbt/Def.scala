/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.net.URI

import scala.annotation.compileTimeOnly
import scala.annotation.tailrec
import scala.annotation.targetName
import sbt.KeyRanks.{ DTask, Invisible }
import sbt.Scope.{ GlobalScope, ThisScope }
import sbt.internal.util.Types.const
import sbt.internal.util.complete.Parser
import sbt.internal.util.{ Terminal => ITerminal, * }
import sbt.util.{ ActionCacheStore, AggregateActionCacheStore }
import Util._
import sbt.util.Show
import xsbti.VirtualFile
import sjsonnew.JsonFormat

/** A concrete settings system that uses `sbt.Scope` for the scope type. */
object Def extends Init[Scope] with TaskMacroExtra with InitializeImplicits:
  type Classpath = Seq[Attributed[File]]
  type VirtualClasspath = Seq[Attributed[VirtualFile]]

  def settings(ss: SettingsDefinition*): Seq[Setting[_]] = ss.flatMap(_.settings)

  val onComplete = SettingKey[() => Unit](
    "onComplete",
    "Hook to run when task evaluation completes.  The type of this setting is subject to change, pending the resolution of SI-2915."
  ) // .withRank(DSetting)
  val triggeredBy = AttributeKey[Seq[Task[_]]]("triggered-by")
  val runBefore = AttributeKey[Seq[Task[_]]]("run-before")
  val resolvedScoped = SettingKey[ScopedKey[_]](
    "resolved-scoped",
    "The ScopedKey for the referencing setting or task.",
    KeyRanks.DSetting
  )
  private[sbt] val taskDefinitionKey = AttributeKey[ScopedKey[_]](
    "task-definition-key",
    "Internal: used to map a task back to its ScopedKey.",
    Invisible
  )

  lazy val showFullKey: Show[ScopedKey[_]] = showFullKey(None)

  def showFullKey(keyNameColor: Option[String]): Show[ScopedKey[_]] =
    Show[ScopedKey[_]]((key: ScopedKey[_]) => displayFull(key, keyNameColor))

  @deprecated("Use showRelativeKey2 which doesn't take the unused multi param", "1.1.1")
  def showRelativeKey(
      current: ProjectRef,
      multi: Boolean,
      keyNameColor: Option[String] = None
  ): Show[ScopedKey[_]] =
    showRelativeKey2(current, keyNameColor)

  def showRelativeKey2(
      current: ProjectRef,
      keyNameColor: Option[String] = None,
  ): Show[ScopedKey[_]] =
    Show[ScopedKey[_]](key => {
      val color: String => String = withColor(_, keyNameColor)
      key.scope.extra.toOption
        .flatMap(_.get(Scope.customShowString).map(color))
        .getOrElse {
          Scope.display(key.scope, color(key.key.label), ref => displayRelative2(current, ref))
        }
    })

  private[sbt] def showShortKey(
      keyNameColor: Option[String],
  ): Show[ScopedKey[_]] = {
    def displayShort(
        project: Reference
    ): String = {
      val trailing = " /"
      project match {
        case BuildRef(_)      => "ThisBuild" + trailing
        case ProjectRef(_, x) => x + trailing
        case _                => Reference.display(project) + trailing
      }
    }
    Show[ScopedKey[_]](key =>
      Scope.display(
        key.scope,
        withColor(key.key.label, keyNameColor),
        ref => displayShort(ref)
      )
    )
  }

  @deprecated("Use showBuildRelativeKey2 which doesn't take the unused multi param", "1.1.1")
  def showBuildRelativeKey(
      currentBuild: URI,
      multi: Boolean,
      keyNameColor: Option[String] = None,
  ): Show[ScopedKey[_]] =
    showBuildRelativeKey2(currentBuild, keyNameColor)

  def showBuildRelativeKey2(
      currentBuild: URI,
      keyNameColor: Option[String] = None,
  ): Show[ScopedKey[_]] =
    Show[ScopedKey[_]](key =>
      Scope.display(
        key.scope,
        withColor(key.key.label, keyNameColor),
        ref => displayBuildRelative(currentBuild, ref)
      )
    )

  /**
   * Returns a String expression for the given [[Reference]] (BuildRef, [[ProjectRef]], etc)
   * relative to the current project.
   */
  def displayRelativeReference(current: ProjectRef, project: Reference): String =
    displayRelative(current, project, false)

  @deprecated("Use displayRelative2 which doesn't take the unused multi param", "1.1.1")
  def displayRelative(current: ProjectRef, multi: Boolean, project: Reference): String =
    displayRelative2(current, project)

  def displayRelative2(current: ProjectRef, project: Reference): String =
    displayRelative(current, project, true)

  /**
   * Constructs the String of a given [[Reference]] relative to current.
   * Note that this no longer takes "multi" parameter, and omits the subproject id at all times.
   */
  private[sbt] def displayRelative(
      current: ProjectRef,
      project: Reference,
      trailingSlash: Boolean
  ): String = {
    import Reference.{ display => displayRef }
    @tailrec def loop(ref: Reference): String = ref match {
      case ProjectRef(b, p) => if (b == current.build) loop(LocalProject(p)) else displayRef(ref)
      case BuildRef(b)      => if (b == current.build) loop(ThisBuild) else displayRef(ref)
      case RootProject(b)   => if (b == current.build) loop(LocalRootProject) else displayRef(ref)
      case LocalProject(p)  => if (p == current.project) "" else p
      case ThisBuild        => "ThisBuild"
      case LocalRootProject => "<root>"
      case ThisProject      => "<this>"
    }
    val str = loop(project)
    if (trailingSlash && !str.isEmpty) s"$str /"
    else str
  }

  @deprecated("Use variant without multi", "1.1.1")
  def displayBuildRelative(currentBuild: URI, multi: Boolean, project: Reference): String =
    displayBuildRelative(currentBuild, project)

  def displayBuildRelative(currentBuild: URI, project: Reference): String =
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

  def withColor(s: String, color: Option[String]): String =
    withColor(s, color, useColor = ITerminal.isColorEnabled)
  def withColor(s: String, color: Option[String], useColor: Boolean): String = color match {
    case Some(c) if useColor => c + s + scala.Console.RESET
    case _                   => s
  }

  override def deriveAllowed[T](s: Setting[T], allowDynamic: Boolean): Option[String] =
    super.deriveAllowed(s, allowDynamic) orElse
      (if s.key.scope != ThisScope then
         Some(s"Scope cannot be defined for ${definedSettingString(s)}")
       else none) orElse
      s.dependencies
        .find(k => k.scope != ThisScope)
        .map(k =>
          s"Scope cannot be defined for dependency ${k.key.label} of ${definedSettingString(s)}"
        )

  override def intersect(s1: Scope, s2: Scope)(implicit
      delegates: Scope => Seq[Scope]
  ): Option[Scope] =
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
  def toITask[A1](i: Initialize[A1]): Initialize[Task[A1]] = map(i)(std.TaskExtra.inlineTask)

  inline def toSParser[A1](p: Parser[A1]): State => Parser[A1] = const(p)
  def toISParser[A1](p: Initialize[Parser[A1]]): Initialize[State => Parser[A1]] =
    p.apply[State => Parser[A1]](toSParser(_))
  def toIParser[A1](p: Initialize[InputTask[A1]]): Initialize[State => Parser[Task[A1]]] =
    p(_.parser)

  import std.SettingMacro.{
    // settingDynMacroImpl,
    settingMacroImpl
  }
  import std.*

  import language.experimental.macros

  private[sbt] var _cacheStore: ActionCacheStore = AggregateActionCacheStore.empty
  def cacheStore: ActionCacheStore = _cacheStore

  inline def cachedTask[A1: JsonFormat](inline a1: A1): Def.Initialize[Task[A1]] =
    ${ TaskMacro.taskMacroImpl[A1]('a1, cached = true) }

  inline def task[A1](inline a1: A1): Def.Initialize[Task[A1]] =
    ${ TaskMacro.taskMacroImpl[A1]('a1, cached = false) }

  inline def taskDyn[A1](inline a1: Def.Initialize[Task[A1]]): Def.Initialize[Task[A1]] =
    ${ TaskMacro.taskDynMacroImpl[A1]('a1) }

  inline def setting[A1](inline a: A1): Def.Initialize[A1] = ${ settingMacroImpl[A1]('a) }

  inline def settingDyn[A1](inline a1: Def.Initialize[A1]): Def.Initialize[A1] =
    ${ SettingMacro.settingDynImpl('a1) }

  inline def input[A1](inline p: State => Parser[A1]): ParserGen[A1] =
    ${ SettingMacro.inputMacroImpl[A1]('p) }

  inline def inputTask[A1](inline a: A1): Def.Initialize[InputTask[A1]] =
    ${ InputTaskMacro.inputTaskMacroImpl[A1]('a) }

  inline def taskIf[A1](inline a: A1): Def.Initialize[Task[A1]] =
    ${ TaskMacro.taskIfImpl[A1]('a, cached = true) }

  private[sbt] def selectITask[A1, A2](
      fab: Initialize[Task[Either[A1, A2]]],
      fin: Initialize[Task[A1 => A2]]
  ): Initialize[Task[A2]] =
    fab.zipWith(fin)((ab, in) => TaskExtra.select(ab, in))

  import Scoped.syntax.{ *, given } // { Def => _, DTask => _, Invisible => _, * }

  // derived from select
  private[sbt] def branchS[A, B, C](
      x: Def.Initialize[Task[Either[A, B]]]
  )(l: Def.Initialize[Task[A => C]])(r: Def.Initialize[Task[B => C]]): Def.Initialize[Task[C]] =
    val lhs: Initialize[Task[Either[B, C]]] = {
      val innerLhs: Def.Initialize[Task[Either[A, Either[B, C]]]] =
        x.map((fab: Either[A, B]) => fab.map(Left(_)))
      val innerRhs: Def.Initialize[Task[A => Either[B, C]]] =
        l.map((fn: A => C) => fn.andThen(Right(_)))
      selectITask[A, Either[B, C]](innerLhs, innerRhs)
    }
    selectITask[B, C](lhs, r)

  // derived from select
  def ifS[A](
      x: Def.Initialize[Task[Boolean]]
  )(t: Def.Initialize[Task[A]])(e: Def.Initialize[Task[A]]): Def.Initialize[Task[A]] =
    val condition: Def.Initialize[Task[Either[Unit, Unit]]] =
      x.map { (p: Boolean) => if p then Left(()) else Right(()) }
    val left: Def.Initialize[Task[Unit => A]] =
      t.map { (a: A) => { (_: Unit) => a } }
    val right: Def.Initialize[Task[Unit => A]] =
      e.map { (a: A) => { (_: Unit) => a } }
    branchS(condition)(left)(right)

  /**
   * Returns `PromiseWrap[A]`, which is a wrapper around `scala.concurrent.Promise`.
   * When a task is typed promise (e.g. `Def.Initialize[Task[PromiseWrap[A]]]`),an implicit
   * method called `await` is injected which will run in a thread outside of concurrent restriction budget.
   */
  def promise[A]: PromiseWrap[A] = new PromiseWrap[A]()

  // The following conversions enable the types Initialize[T], Initialize[Task[T]], and Task[T] to
  //  be used in task and setting macros as inputs with an ultimate result of type T

  // implicit def macroValueI[T](@deprecated("unused", "") in: Initialize[T]): MacroValue[T] = ???

  extension [A1](inline in: Initialize[A1])
    inline def value: A1 = InputWrapper.`wrapInit_\u2603\u2603`[A1](in)

  extension [A1](inline in: Initialize[Task[A1]])
    @targetName("valueIA1")
    inline def value: A1 = InputWrapper.`wrapInitTask_\u2603\u2603`[A1](in)

    /**
     * This treats the `Initailize[Task[A]]` as a setting that returns the Task value,
     * instead of evaluating the task.
     */
    inline def taskValue: Task[A1] = InputWrapper.`wrapInit_\u2603\u2603`[Task[A1]](in)

    // implicit def macroValueIInT[T](
    //     @deprecated("unused", "") in: Initialize[InputTask[T]]
    // ): InputEvaluated[T] = ???

    inline def flatMapTask[A2](f: A1 => Initialize[Task[A2]]): Initialize[Task[A2]] =
      std.FullInstance.initializeTaskMonad.flatMap(in)(f)

  extension [A1](inline in: TaskKey[A1])
    // implicit def macroPrevious[T](@deprecated("unused", "") in: TaskKey[T]): MacroPrevious[T] = ???
    inline def previous(using JsonFormat[A1]): Option[A1] =
      ${ TaskMacro.previousImpl[A1]('in) }

  // The following conversions enable the types Parser[T], Initialize[Parser[T]], and
  // Initialize[State => Parser[T]] to be used in the inputTask macro as an input with an ultimate
  // result of type A1, previously implemented using ParserInput.parsedMacroImpl[A1].

  extension [A1](inline in: Initialize[Parser[A1]])
    inline def parsed: A1 = ParserInput.`initParser_\u2603\u2603`[A1](Def.toISParser(in))

  extension [A1](inline in: Initialize[State => Parser[A1]])
    @targetName("parsedISPA1")
    inline def parsed: A1 = ParserInput.`initParser_\u2603\u2603`[A1](in)

  extension [A1](inline in: Def.Initialize[InputTask[A1]])
    inline def parsed: Task[A1] =
      ParserInput.`initParser_\u2603\u2603`[Task[A1]](Def.toIParser[A1](in))

    inline def evaluated: A1 = InputWrapper.`wrapInitInputTask_\u2603\u2603`[A1](in)

    inline def toTask(arg: String): Initialize[Task[A1]] =
      import TaskExtra.singleInputTask
      FullInstance.flatten(
        (Def.stateKey zipWith in)((sTask, it) =>
          sTask map { s =>
            Parser.parse(arg, it.parser(s)) match
              case Right(a) => Def.value[Task[A1]](a)
              case Left(msg) =>
                val indented = msg.linesIterator.map("   " + _).mkString("\n")
                sys.error(s"Invalid programmatic input:\n$indented")
          }
        )
      )

  inline def settingKey[A1](inline description: String): SettingKey[A1] =
    ${ std.KeyMacro.settingKeyImpl[A1]('description) }

  inline def taskKey[A1](inline description: String): TaskKey[A1] =
    ${ std.KeyMacro.taskKeyImpl[A1]('description) }

  inline def inputKey[A1](inline description: String): InputKey[A1] =
    ${ std.KeyMacro.inputKeyImpl[A1]('description) }

  class InitOps[T](private val x: Initialize[T]) extends AnyVal {
    def toTaskable: Taskable[T] = x
  }

  class InitTaskOps[T](private val x: Initialize[Task[T]]) extends AnyVal {
    def toTaskable: Taskable[T] = x
  }

  /**
   * This works around Scala 2.12.12's
   * "a pure expression does nothing in statement position"
   *
   * {{{
   * Def.unit(copyResources.value)
   * Def.unit(compile.value)
   * }}}
   */
  def unit(a: Any): Unit = ()

  private[sbt] def dummy[A: Manifest](name: String, description: String): (TaskKey[A], Task[A]) =
    (TaskKey[A](name, description, DTask), dummyTask(name))

  private[sbt] def dummyTask[T](name: String): Task[T] = {
    import std.TaskExtra.{ task => newTask, toTaskInfo }
    val base: Task[T] = newTask(
      sys.error("Dummy task '" + name + "' did not get converted to a full task.")
    )
      .named(name)
    base.copy(info = base.info.set(isDummyTask, true))
  }

  private[sbt] def isDummy(t: Task[_]): Boolean =
    t.info.attributes.get(isDummyTask) getOrElse false

  private[sbt] val isDummyTask = AttributeKey[Boolean](
    "is-dummy-task",
    "Internal: used to identify dummy tasks.  sbt injects values for these tasks at the start of task execution.",
    Invisible
  )

  private[sbt] val (stateKey: TaskKey[State], dummyState: Task[State]) =
    dummy[State]("state", "Current build state.")

  private[sbt] val (
    streamsManagerKey: TaskKey[std.Streams[ScopedKey[_]]],
    dummyStreamsManager: Task[std.Streams[ScopedKey[_]]]
  ) =
    Def.dummy[std.Streams[ScopedKey[_]]](
      "streams-manager",
      "Streams manager, which provides streams for different contexts."
    )
end Def

// these need to be mixed into the sbt package object
// because the target doesn't involve Initialize or anything in Def
trait TaskMacroExtra:
  import sbt.std.ParserInput
  extension [A1](inline in: Task[A1])
    inline def value: A1 = std.InputWrapper.`wrapTask_\u2603\u2603`[A1](in)

  // implicit def macroValueIn[T](@deprecated("unused", "") in: InputTask[T]): std.InputEvaluated[T] =
  //   ???

  extension [A1](inline in: Parser[A1])
    inline def parsed: A1 = ParserInput.`parser_\u2603\u2603`[A1](Def.toSParser(in))

  extension [A1](inline in: State => Parser[A1])
    inline def parsed: A1 = ParserInput.`parser_\u2603\u2603`[A1](in)
end TaskMacroExtra

sealed trait InitializeImplicits0 { self: Def.type =>
  implicit def initOps[T](x: Def.Initialize[T]): Def.InitOps[T] = new Def.InitOps(x)
}

sealed trait InitializeImplicits extends InitializeImplicits0 { self: Def.type =>
  implicit def initTaskOps[T](x: Def.Initialize[Task[T]]): Def.InitTaskOps[T] =
    new Def.InitTaskOps(x)
}
