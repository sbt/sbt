/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.net.URI

import scala.annotation.tailrec
import sbt.KeyRanks.{ DTask, Invisible }
import sbt.Scope.{ GlobalScope, ThisScope }
import sbt.internal.util.Types.const
import sbt.internal.util.complete.Parser
import sbt.internal.util.{ Terminal => ITerminal, _ }
import Util._
import sbt.util.Show
import xsbti.VirtualFile

/** A concrete settings system that uses `sbt.Scope` for the scope type. */
object Def extends Init[Scope] with TaskMacroExtra with InitializeImplicits {
  type Classpath = Seq[Attributed[File]]
  type VirtualClasspath = Seq[Attributed[VirtualFile]]

  def settings(ss: SettingsDefinition*): Seq[Setting[_]] = ss.flatMap(_.settings)

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
    Show[ScopedKey[_]](
      key => {
        val color: String => String = withColor(_, keyNameColor)
        key.scope.extra.toOption
          .flatMap(_.get(Scope.customShowString).map(color))
          .getOrElse {
            Scope.display(key.scope, color(key.key.label), ref => displayRelative2(current, ref))
          }
      }
    )

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
    Show[ScopedKey[_]](
      key =>
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
    Show[ScopedKey[_]](
      key =>
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
      (if (s.key.scope != ThisScope)
         s"Scope cannot be defined for ${definedSettingString(s)}".some
       else none) orElse
      s.dependencies
        .find(k => k.scope != ThisScope)
        .map(
          k =>
            s"Scope cannot be defined for dependency ${k.key.label} of ${definedSettingString(s)}"
        )

  override def intersect(s1: Scope, s2: Scope)(
      implicit delegates: Scope => Seq[Scope]
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
  def toITask[T](i: Initialize[T]): Initialize[Task[T]] = map(i)(std.TaskExtra.inlineTask)

  def toSParser[T](p: Parser[T]): State => Parser[T] = const(p)
  def toISParser[T](p: Initialize[Parser[T]]): Initialize[State => Parser[T]] = p(toSParser)
  def toIParser[T](p: Initialize[InputTask[T]]): Initialize[State => Parser[Task[T]]] = p(_.parser)

  import std.SettingMacro.{ settingDynMacroImpl, settingMacroImpl }
  import std.TaskMacro.{
    inputTaskDynMacroImpl,
    inputTaskMacroImpl,
    taskDynMacroImpl,
    taskIfMacroImpl,
    taskMacroImpl
  }
  import std._

  import language.experimental.macros

  def task[T](t: T): Def.Initialize[Task[T]] = macro taskMacroImpl[T]
  def taskDyn[T](t: Def.Initialize[Task[T]]): Def.Initialize[Task[T]] = macro taskDynMacroImpl[T]
  def setting[T](t: T): Def.Initialize[T] = macro settingMacroImpl[T]
  def settingDyn[T](t: Def.Initialize[T]): Def.Initialize[T] = macro settingDynMacroImpl[T]
  def inputTask[T](t: T): Def.Initialize[InputTask[T]] = macro inputTaskMacroImpl[T]
  def inputTaskDyn[T](t: Def.Initialize[Task[T]]): Def.Initialize[InputTask[T]] =
    macro inputTaskDynMacroImpl[T]
  def taskIf[T](a: T): Def.Initialize[Task[T]] = macro taskIfMacroImpl[T]

  private[sbt] def selectITask[A, B](
      fab: Initialize[Task[Either[A, B]]],
      fin: Initialize[Task[A => B]]
  ): Initialize[Task[B]] =
    fab.zipWith(fin)((ab, in) => TaskExtra.select(ab, in))

  import Scoped.syntax._

  // derived from select
  private[sbt] def branchS[A, B, C](
      x: Def.Initialize[Task[Either[A, B]]]
  )(l: Def.Initialize[Task[A => C]])(r: Def.Initialize[Task[B => C]]): Def.Initialize[Task[C]] = {
    val lhs = {
      val innerLhs: Def.Initialize[Task[Either[A, Either[B, C]]]] =
        x.map((fab: Either[A, B]) => fab.right.map(Left(_)))
      val innerRhs: Def.Initialize[Task[A => Either[B, C]]] =
        l.map((fn: A => C) => fn.andThen(Right(_)))
      selectITask(innerLhs, innerRhs)
    }
    selectITask(lhs, r)
  }

  // derived from select
  def ifS[A](
      x: Def.Initialize[Task[Boolean]]
  )(t: Def.Initialize[Task[A]])(e: Def.Initialize[Task[A]]): Def.Initialize[Task[A]] = {
    val condition: Def.Initialize[Task[Either[Unit, Unit]]] =
      x.map((p: Boolean) => if (p) Left(()) else Right(()))
    val left: Def.Initialize[Task[Unit => A]] =
      t.map((a: A) => { _ => a })
    val right: Def.Initialize[Task[Unit => A]] =
      e.map((a: A) => { _ => a })
    branchS(condition)(left)(right)
  }

  /** Returns `PromiseWrap[A]`, which is a wrapper around `scala.concurrent.Promise`.
   * When a task is typed promise (e.g. `Def.Initialize[Task[PromiseWrap[A]]]`),an implicit
   * method called `await` is injected which will run in a thread outside of concurrent restriction budget.
   */
  def promise[A]: PromiseWrap[A] = new PromiseWrap[A]()

  // The following conversions enable the types Initialize[T], Initialize[Task[T]], and Task[T] to
  //  be used in task and setting macros as inputs with an ultimate result of type T

  implicit def macroValueI[T](@deprecated("unused", "") in: Initialize[T]): MacroValue[T] = ???

  implicit def macroValueIT[T](@deprecated("unused", "") in: Initialize[Task[T]]): MacroValue[T] =
    ???

  implicit def macroValueIInT[T](
      @deprecated("unused", "") in: Initialize[InputTask[T]]
  ): InputEvaluated[T] = ???

  implicit def taskMacroValueIT[T](
      @deprecated("unused", "") in: Initialize[Task[T]]
  ): MacroTaskValue[T] = ???

  implicit def macroPrevious[T](@deprecated("unused", "") in: TaskKey[T]): MacroPrevious[T] = ???

  // The following conversions enable the types Parser[T], Initialize[Parser[T]], and
  // Initialize[State => Parser[T]] to be used in the inputTask macro as an input with an ultimate
  // result of type T
  implicit def parserInitToInput[T](
      @deprecated("unused", "") p: Initialize[Parser[T]]
  ): ParserInput[T] = ???

  implicit def parserInitStateToInput[T](
      @deprecated("unused", "") p: Initialize[State => Parser[T]]
  ): ParserInput[T] = ???

  def settingKey[T](description: String): SettingKey[T] = macro std.KeyMacro.settingKeyImpl[T]
  def taskKey[T](description: String): TaskKey[T] = macro std.KeyMacro.taskKeyImpl[T]
  def inputKey[T](description: String): InputKey[T] = macro std.KeyMacro.inputKeyImpl[T]

  class InitOps[T](private val x: Initialize[T]) extends AnyVal {
    def toTaskable: Taskable[T] = x
  }

  class InitTaskOps[T](private val x: Initialize[Task[T]]) extends AnyVal {
    def toTaskable: Taskable[T] = x
  }

  /** This works around Scala 2.12.12's
   * "a pure expression does nothing in statement position"
   *
   * {{{
   * Def.unit(copyResources.value)
   * Def.unit(compile.value)
   * }}}
   */
  def unit(a: Any): Unit = ()

  private[sbt] def dummy[T: Manifest](name: String, description: String): (TaskKey[T], Task[T]) =
    (TaskKey[T](name, description, DTask), dummyTask(name))

  private[sbt] def dummyTask[T](name: String): Task[T] = {
    import std.TaskExtra.{ task => newTask, _ }
    val base: Task[T] = newTask(
      sys.error("Dummy task '" + name + "' did not get converted to a full task.")
    ) named name
    base.copy(info = base.info.set(isDummyTask, true))
  }

  private[sbt] def isDummy(t: Task[_]): Boolean =
    t.info.attributes.get(isDummyTask) getOrElse false

  private[sbt] val isDummyTask = AttributeKey[Boolean](
    "is-dummy-task",
    "Internal: used to identify dummy tasks.  sbt injects values for these tasks at the start of task execution.",
    Invisible
  )

  private[sbt] val (stateKey, dummyState) = dummy[State]("state", "Current build state.")

  private[sbt] val (streamsManagerKey, dummyStreamsManager) = Def.dummy[std.Streams[ScopedKey[_]]](
    "streams-manager",
    "Streams manager, which provides streams for different contexts."
  )
}

// these need to be mixed into the sbt package object
// because the target doesn't involve Initialize or anything in Def
trait TaskMacroExtra {
  implicit def macroValueT[T](@deprecated("unused", "") in: Task[T]): std.MacroValue[T] = ???

  implicit def macroValueIn[T](@deprecated("unused", "") in: InputTask[T]): std.InputEvaluated[T] =
    ???

  implicit def parserToInput[T](@deprecated("unused", "") in: Parser[T]): std.ParserInput[T] = ???

  implicit def stateParserToInput[T](
      @deprecated("unused", "") in: State => Parser[T]
  ): std.ParserInput[T] = ???
}

sealed trait InitializeImplicits0 { self: Def.type =>
  implicit def initOps[T](x: Def.Initialize[T]): Def.InitOps[T] = new Def.InitOps(x)
}

sealed trait InitializeImplicits extends InitializeImplicits0 { self: Def.type =>
  implicit def initTaskOps[T](x: Def.Initialize[Task[T]]): Def.InitTaskOps[T] =
    new Def.InitTaskOps(x)
}
