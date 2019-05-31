/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import scala.language.experimental.macros

import sbt.internal.util.Types._
import sbt.internal.util.{ AttributeKey, Settings, SourcePosition }
import sbt.util.OptJsonWriter
import sbt.ConcurrentRestrictions.Tag
import sbt.Def.{ Initialize, KeyedInitialize, ScopedKey, Setting, setting }
import std.TaskExtra.{ task => mktask, _ }

/** An abstraction on top of Settings for build configuration and task definition. */
sealed trait Scoped extends Equals {
  def scope: Scope
  val key: AttributeKey[_]

  override def equals(that: Any) =
    (this eq that.asInstanceOf[AnyRef]) || (that match {
      case that: Scoped => scope == that.scope && key == that.key && canEqual(that)
      case _            => false
    })

  override def hashCode() = (scope, key).##
}

/** A common type for SettingKey and TaskKey so that both can be used as inputs to tasks.*/
sealed trait ScopedTaskable[T] extends Scoped {
  def toTask: Initialize[Task[T]]
}

/**
 * Identifies a setting.  It consists of three parts: the scope, the name, and the type of a value associated with this key.
 * The scope is represented by a value of type Scope.
 * The name and the type are represented by a value of type `AttributeKey[T]`.
 * Instances are constructed using the companion object.
 */
sealed abstract class SettingKey[T]
    extends ScopedTaskable[T]
    with KeyedInitialize[T]
    with Scoped.ScopingSetting[SettingKey[T]]
    with Scoped.DefinableSetting[T] {

  val key: AttributeKey[T]

  override def toString: String = s"SettingKey($scope / $key)"

  final def toTask: Initialize[Task[T]] = this apply inlineTask

  final def scopedKey: ScopedKey[T] = ScopedKey(scope, key)

  final def in(scope: Scope): SettingKey[T] =
    Scoped.scopedSetting(Scope.replaceThis(this.scope)(scope), this.key)

  final def :=(v: T): Setting[T] = macro std.TaskMacro.settingAssignMacroImpl[T]

  final def +=[U](v: U)(implicit a: Append.Value[T, U]): Setting[T] =
    macro std.TaskMacro.settingAppend1Impl[T, U]

  final def ++=[U](vs: U)(implicit a: Append.Values[T, U]): Setting[T] =
    macro std.TaskMacro.settingAppendNImpl[T, U]

  final def <+=[V](v: Initialize[V])(implicit a: Append.Value[T, V]): Setting[T] =
    macro std.TaskMacro.fakeSettingAppend1Position[T, V]

  final def <++=[V](vs: Initialize[V])(implicit a: Append.Values[T, V]): Setting[T] =
    macro std.TaskMacro.fakeSettingAppendNPosition[T, V]

  final def -=[U](v: U)(implicit r: Remove.Value[T, U]): Setting[T] =
    macro std.TaskMacro.settingRemove1Impl[T, U]

  final def --=[U](vs: U)(implicit r: Remove.Values[T, U]): Setting[T] =
    macro std.TaskMacro.settingRemoveNImpl[T, U]

  final def ~=(f: T => T): Setting[T] = macro std.TaskMacro.settingTransformPosition[T]

  final def append1[V](v: Initialize[V], source: SourcePosition)(
      implicit a: Append.Value[T, V]
  ): Setting[T] = make(v, source)(a.appendValue)

  final def appendN[V](vs: Initialize[V], source: SourcePosition)(
      implicit a: Append.Values[T, V]
  ): Setting[T] = make(vs, source)(a.appendValues)

  final def remove1[V](v: Initialize[V], source: SourcePosition)(
      implicit r: Remove.Value[T, V]
  ): Setting[T] = make(v, source)(r.removeValue)
  final def removeN[V](vs: Initialize[V], source: SourcePosition)(
      implicit r: Remove.Values[T, V]
  ): Setting[T] = make(vs, source)(r.removeValues)

  final def transform(f: T => T, source: SourcePosition): Setting[T] = set(scopedKey(f), source)

  protected[this] def make[S](other: Initialize[S], source: SourcePosition)(
      f: (T, S) => T
  ): Setting[T] = set(this.zipWith(other)(f), source)

  final def withRank(rank: Int): SettingKey[T] =
    SettingKey(AttributeKey.copyWithRank(key, rank))

  def canEqual(that: Any): Boolean = that.isInstanceOf[SettingKey[_]]
}

/**
 * Identifies a task.  It consists of three parts: the scope, the name, and the type of the value computed by a task associated with this key.
 * The scope is represented by a value of type Scope.
 * The name and the type are represented by a value of type `AttributeKey[Task[T]]`.
 * Instances are constructed using the companion object.
 */
sealed abstract class TaskKey[T]
    extends ScopedTaskable[T]
    with KeyedInitialize[Task[T]]
    with Scoped.ScopingSetting[TaskKey[T]]
    with Scoped.DefinableTask[T] {

  val key: AttributeKey[Task[T]]

  override def toString: String = s"TaskKey($scope / $key)"

  def toTask: Initialize[Task[T]] = this

  def scopedKey: ScopedKey[Task[T]] = ScopedKey(scope, key)

  def in(scope: Scope): TaskKey[T] =
    Scoped.scopedTask(Scope.replaceThis(this.scope)(scope), this.key)

  def +=[U](v: U)(implicit a: Append.Value[T, U]): Setting[Task[T]] =
    macro std.TaskMacro.taskAppend1Impl[T, U]

  def ++=[U](vs: U)(implicit a: Append.Values[T, U]): Setting[Task[T]] =
    macro std.TaskMacro.taskAppendNImpl[T, U]

  def <+=[V](v: Initialize[Task[V]])(implicit a: Append.Value[T, V]): Setting[Task[T]] =
    macro std.TaskMacro.fakeTaskAppend1Position[T, V]

  def <++=[V](vs: Initialize[Task[V]])(implicit a: Append.Values[T, V]): Setting[Task[T]] =
    macro std.TaskMacro.fakeTaskAppendNPosition[T, V]

  final def -=[U](v: U)(implicit r: Remove.Value[T, U]): Setting[Task[T]] =
    macro std.TaskMacro.taskRemove1Impl[T, U]

  final def --=[U](vs: U)(implicit r: Remove.Values[T, U]): Setting[Task[T]] =
    macro std.TaskMacro.taskRemoveNImpl[T, U]

  def append1[V](v: Initialize[Task[V]], source: SourcePosition)(
      implicit a: Append.Value[T, V]
  ): Setting[Task[T]] = make(v, source)(a.appendValue)

  def appendN[V](vs: Initialize[Task[V]], source: SourcePosition)(
      implicit a: Append.Values[T, V]
  ): Setting[Task[T]] = make(vs, source)(a.appendValues)

  final def remove1[V](v: Initialize[Task[V]], source: SourcePosition)(
      implicit r: Remove.Value[T, V]
  ): Setting[Task[T]] = make(v, source)(r.removeValue)

  final def removeN[V](vs: Initialize[Task[V]], source: SourcePosition)(
      implicit r: Remove.Values[T, V]
  ): Setting[Task[T]] = make(vs, source)(r.removeValues)

  private[this] def make[S](other: Initialize[Task[S]], source: SourcePosition)(
      f: (T, S) => T
  ): Setting[Task[T]] = set(this.zipWith(other)((a, b) => (a, b) map f.tupled), source)

  final def withRank(rank: Int): TaskKey[T] =
    TaskKey(AttributeKey.copyWithRank(key, rank))

  def canEqual(that: Any): Boolean = that.isInstanceOf[TaskKey[_]]
}

/**
 * Identifies an input task.  An input task parses input and produces a task to run.
 * It consists of three parts: the scope, the name, and the type of the value produced by an input task associated with this key.
 * The scope is represented by a value of type Scope.
 * The name and the type are represented by a value of type `AttributeKey[InputTask[T]]`.
 * Instances are constructed using the companion object.
 */
sealed trait InputKey[T]
    extends Scoped
    with KeyedInitialize[InputTask[T]]
    with Scoped.ScopingSetting[InputKey[T]]
    with Scoped.DefinableSetting[InputTask[T]] {

  val key: AttributeKey[InputTask[T]]

  override def toString: String = s"InputKey($scope / $key)"

  def scopedKey: ScopedKey[InputTask[T]] = ScopedKey(scope, key)

  def in(scope: Scope): InputKey[T] =
    Scoped.scopedInput(Scope.replaceThis(this.scope)(scope), this.key)

  final def :=(v: T): Setting[InputTask[T]] = macro std.TaskMacro.inputTaskAssignMacroImpl[T]
  final def ~=(f: T => T): Setting[InputTask[T]] = macro std.TaskMacro.itaskTransformPosition[T]

  final def transform(f: T => T, source: SourcePosition): Setting[InputTask[T]] =
    set(scopedKey(_ mapTask { _ map f }), source)

  final def withRank(rank: Int): InputKey[T] =
    InputKey(AttributeKey.copyWithRank(key, rank))

  def canEqual(that: Any): Boolean = that.isInstanceOf[InputKey[_]]
}

/** Methods and types related to constructing settings, including keys, scopes, and initializations. */
object Scoped extends ScopedArity {
  implicit def taskScopedToKey[T](s: TaskKey[T]): ScopedKey[Task[T]] = ScopedKey(s.scope, s.key)

  implicit def inputScopedToKey[T](s: InputKey[T]): ScopedKey[InputTask[T]] =
    ScopedKey(s.scope, s.key)

  /**
   * Mixin trait for adding convenience vocabulary associated with specifying the [[Scope]] of a setting.
   * Allows specification of the Scope or part of the [[Scope]] of a setting being referenced.
   * @example
   *  {{{
   *  name in Global := "hello Global scope"
   *
   *  name in (Compile, packageBin) := "hello Compile scope packageBin"
   *
   *  name in Compile := "hello Compile scope"
   *
   *  name.in(Compile).:=("hello ugly syntax")
   *  }}}
   *
   */
  sealed trait ScopingSetting[ResultType] {
    def in(s: Scope): ResultType

    def in(p: Reference): ResultType = in(Select(p), This, This)
    def in(t: Scoped): ResultType = in(This, This, Select(t.key))
    def in(c: ConfigKey): ResultType = in(This, Select(c), This)
    def in(c: ConfigKey, t: Scoped): ResultType = in(This, Select(c), Select(t.key))
    def in(p: Reference, c: ConfigKey): ResultType = in(Select(p), Select(c), This)
    def in(p: Reference, t: Scoped): ResultType = in(Select(p), This, Select(t.key))

    def in(p: Reference, c: ConfigKey, t: Scoped): ResultType =
      in(Select(p), Select(c), Select(t.key))

    def in(
        p: ScopeAxis[Reference],
        c: ScopeAxis[ConfigKey],
        t: ScopeAxis[AttributeKey[_]]
    ): ResultType = in(Scope(p, c, t, This))
  }

  def scopedSetting[T](s: Scope, k: AttributeKey[T]): SettingKey[T] =
    new SettingKey[T] { val scope = s; val key = k }

  def scopedInput[T](s: Scope, k: AttributeKey[InputTask[T]]): InputKey[T] =
    new InputKey[T] { val scope = s; val key = k }

  def scopedTask[T](s: Scope, k: AttributeKey[Task[T]]): TaskKey[T] =
    new TaskKey[T] { val scope = s; val key = k }

  /**
   * Mixin trait for adding convenience vocabulary associated with applying a setting to a configuration item.
   */
  sealed trait DefinableSetting[S] {
    def scopedKey: ScopedKey[S]

    private[sbt] final def :==(app: S): Setting[S] = macro std.TaskMacro.settingAssignPure[S]

    final def <<=(app: Initialize[S]): Setting[S] =
      macro std.TaskMacro.fakeSettingAssignPosition[S]

    /** Internally used function for setting a value along with the `.sbt` file location where it is defined. */
    final def set(app: Initialize[S], source: SourcePosition): Setting[S] =
      setting(scopedKey, app, source)

    /** From the given `Settings`, extract the value bound to this key. */
    final def get(settings: Settings[Scope]): Option[S] =
      settings.get(scopedKey.scope, scopedKey.key)

    /**
     * Creates an [[Def.Initialize]] with value `scala.None` if there was no previous definition of this key,
     * and `scala.Some(value)` if a definition exists. Useful for when you want to use the ''existence'' of
     * one setting in order to define another setting.
     * @return currently bound value wrapped in `Initialize[Some[T]]`, or `Initialize[None]` if unbound.
     */
    final def ? : Initialize[Option[S]] = Def.optional(scopedKey)(idFun)

    /**
     * Creates an [[Def.Initialize]] with value bound to this key, or returns `i` parameter if unbound.
     * @param i value to return if this setting doesn't have a value.
     * @return currently bound setting value, or `i` if unbound.
     */
    final def or[T >: S](i: Initialize[T]): Initialize[T] = ?.zipWith(i)(_.getOrElse(_))

    /**
     * Like [[?]], but with a call-by-name parameter rather than an existing [[Def.Initialize]].
     * Useful when you want to have a value computed when no value is bound to this key.
     * @param or by-name expression evaluated when a value is needed.
     * @return currently bound setting value, or the result of `or` if unbound.
     */
    final def ??[T >: S](or: => T): Initialize[T] = Def.optional(scopedKey)(_ getOrElse or)
  }

  /**
   * Wraps an [[sbt.Def.Initialize]] instance to provide `map` and `flatMap` semantics.
   */
  final class RichInitialize[S](init: Initialize[S]) {
    def map[T](f: S => T): Initialize[Task[T]] = init(s => mktask(f(s)))
    def flatMap[T](f: S => Task[T]): Initialize[Task[T]] = init(f)
  }
  sealed trait DefinableTask[S] { self: TaskKey[S] =>

    private[sbt] def :==(app: S): Setting[Task[S]] = macro std.TaskMacro.taskAssignPositionPure[S]

    private[sbt] def ::=(app: Task[S]): Setting[Task[S]] =
      macro std.TaskMacro.taskAssignPositionT[S]

    def :=(v: S): Setting[Task[S]] = macro std.TaskMacro.taskAssignMacroImpl[S]
    def ~=(f: S => S): Setting[Task[S]] = macro std.TaskMacro.taskTransformPosition[S]

    def <<=(app: Initialize[Task[S]]): Setting[Task[S]] =
      macro std.TaskMacro.fakeItaskAssignPosition[S]

    def set(app: Initialize[Task[S]], source: SourcePosition): Setting[Task[S]] =
      Def.setting(scopedKey, app, source)

    def transform(f: S => S, source: SourcePosition): Setting[Task[S]] =
      set(scopedKey(_ map f), source)

    @deprecated(
      "No longer needed with new task syntax and SettingKey inheriting from Initialize.",
      "0.13.2"
    )
    def task: SettingKey[Task[S]] = scopedSetting(scope, key)

    def toSettingKey: SettingKey[Task[S]] = scopedSetting(scope, key)

    def get(settings: Settings[Scope]): Option[Task[S]] = settings.get(scope, key)

    def ? : Initialize[Task[Option[S]]] = Def.optional(scopedKey) {
      case None => mktask { None }; case Some(t) => t map some.fn
    }

    def ??[T >: S](or: => T): Initialize[Task[T]] = Def.optional(scopedKey)(_ getOrElse mktask(or))

    def or[T >: S](i: Initialize[Task[T]]): Initialize[Task[T]] =
      (this.? zipWith i)((x, y) => (x, y) map { case (a, b) => a getOrElse b })
  }

  /** Enriches `Initialize[Task[S]]` types.
   *
   * @param i the original `Initialize[Task[S]]` value to enrich
   * @tparam S the type of the underlying value
   */
  final class RichInitializeTask[S](i: Initialize[Task[S]]) extends RichInitTaskBase[S, Task] {
    protected def onTask[T](f: Task[S] => Task[T]): Initialize[Task[T]] = i apply f

    def dependsOn(tasks: AnyInitTask*): Initialize[Task[S]] = {
      i.zipWith(Initialize.joinAny[Task](tasks))((thisTask, deps) => thisTask.dependsOn(deps: _*))
    }

    def failure: Initialize[Task[Incomplete]] = i(_.failure)
    def result: Initialize[Task[Result[S]]] = i(_.result)

    def xtriggeredBy[T](tasks: Initialize[Task[T]]*): Initialize[Task[S]] =
      nonLocal(tasks, Def.triggeredBy)

    def triggeredBy[T](tasks: Initialize[Task[T]]*): Initialize[Task[S]] =
      nonLocal(tasks, Def.triggeredBy)

    def runBefore[T](tasks: Initialize[Task[T]]*): Initialize[Task[S]] =
      nonLocal(tasks, Def.runBefore)

    private[this] def nonLocal(
        tasks: Seq[AnyInitTask],
        key: AttributeKey[Seq[Task[_]]]
    ): Initialize[Task[S]] =
      Initialize.joinAny[Task](tasks).zipWith(i)((ts, i) => i.copy(info = i.info.set(key, ts)))
  }

  /** Enriches `Initialize[InputTask[S]]` types.
   *
   * @param i the original `Initialize[InputTask[S]]` value to enrich
   * @tparam S the type of the underlying value
   */
  final class RichInitializeInputTask[S](i: Initialize[InputTask[S]])
      extends RichInitTaskBase[S, InputTask] {

    protected def onTask[T](f: Task[S] => Task[T]): Initialize[InputTask[T]] = i(_ mapTask f)

    def dependsOn(tasks: AnyInitTask*): Initialize[InputTask[S]] = {
      i.zipWith(Initialize.joinAny[Task](tasks))(
        (thisTask, deps) => thisTask.mapTask(_.dependsOn(deps: _*))
      )
    }
  }

  /** Enriches `Initialize[R[S]]` types. Abstracts over the specific task-like type constructor.
   *
   * @tparam S the type of the underlying vault
   * @tparam R the task-like type constructor (either Task or InputTask)
   */
  sealed abstract class RichInitTaskBase[S, R[_]] {
    protected def onTask[T](f: Task[S] => Task[T]): Initialize[R[T]]

    def flatMap[T](f: S => Task[T]): Initialize[R[T]] =
      onTask(_.result flatMap (f compose successM))

    def map[T](f: S => T): Initialize[R[T]] = onTask(_.result map (f compose successM))
    def andFinally(fin: => Unit): Initialize[R[S]] = onTask(_ andFinally fin)
    def doFinally(t: Task[Unit]): Initialize[R[S]] = onTask(_ doFinally t)

    def ||[T >: S](alt: Task[T]): Initialize[R[T]] = onTask(_ || alt)
    def &&[T](alt: Task[T]): Initialize[R[T]] = onTask(_ && alt)

    def tag(tags: Tag*): Initialize[R[S]] = onTask(_.tag(tags: _*))
    def tagw(tags: (Tag, Int)*): Initialize[R[S]] = onTask(_.tagw(tags: _*))

    @deprecated(
      "Use the `result` method to create a task that returns the full Result of this task.  Then, call `flatMap` on the new task.",
      "0.13.0"
    )
    def flatMapR[T](f: Result[S] => Task[T]): Initialize[R[T]] = onTask(_.result flatMap f)

    @deprecated(
      "Use the `result` method to create a task that returns the full Result of this task.  Then, call `map` on the new task.",
      "0.13.0"
    )
    def mapR[T](f: Result[S] => T): Initialize[R[T]] = onTask(_.result map f)

    @deprecated(
      "Use the `failure` method to create a task that returns Incomplete when this task fails and then call `flatMap` on the new task.",
      "0.13.0"
    )
    def flatFailure[T](f: Incomplete => Task[T]): Initialize[R[T]] =
      onTask(_.result flatMap (f compose failM))

    @deprecated(
      "Use the `failure` method to create a task that returns Incomplete when this task fails and then call `map` on the new task.",
      "0.13.0"
    )
    def mapFailure[T](f: Incomplete => T): Initialize[R[T]] = onTask(_.result map (f compose failM))
  }

  type AnyInitTask = Initialize[Task[T]] forSome { type T }

  implicit def richTaskSeq[T](in: Seq[Initialize[Task[T]]]): RichTaskSeq[T] = new RichTaskSeq(in)
  final class RichTaskSeq[T](keys: Seq[Initialize[Task[T]]]) {
    def join: Initialize[Task[Seq[T]]] = tasks(_.join)
    def tasks: Initialize[Seq[Task[T]]] = Initialize.join(keys)
  }

  implicit def richAnyTaskSeq(in: Seq[AnyInitTask]): RichAnyTaskSeq = new RichAnyTaskSeq(in)
  final class RichAnyTaskSeq(keys: Seq[AnyInitTask]) {
    def dependOn: Initialize[Task[Unit]] =
      Initialize.joinAny[Task](keys).apply(deps => nop.dependsOn(deps: _*))
  }

  private[sbt] def extendScoped(s1: Scoped, ss: Seq[Scoped]): Seq[AttributeKey[_]] =
    s1.key +: ss.map(_.key)
}

import Scoped.extendScoped

/** Constructs InputKeys, which are associated with input tasks to define a setting.*/
object InputKey {
  def apply[T: Manifest](
      label: String,
      description: String = "",
      rank: Int = KeyRanks.DefaultInputRank
  ): InputKey[T] =
    apply(AttributeKey[InputTask[T]](label, description, rank))

  def apply[T: Manifest](
      label: String,
      description: String,
      extend1: Scoped,
      extendN: Scoped*
  ): InputKey[T] = apply(label, description, KeyRanks.DefaultInputRank, extend1, extendN: _*)

  def apply[T: Manifest](
      label: String,
      description: String,
      rank: Int,
      extend1: Scoped,
      extendN: Scoped*
  ): InputKey[T] =
    apply(AttributeKey[InputTask[T]](label, description, extendScoped(extend1, extendN), rank))

  def apply[T](akey: AttributeKey[InputTask[T]]): InputKey[T] =
    Scoped.scopedInput(Scope.ThisScope, akey)
}

/** Constructs TaskKeys, which are associated with tasks to define a setting.*/
object TaskKey {
  def apply[T: Manifest](
      label: String,
      description: String = "",
      rank: Int = KeyRanks.DefaultTaskRank
  ): TaskKey[T] =
    apply(AttributeKey[Task[T]](label, description, rank))

  def apply[T: Manifest](
      label: String,
      description: String,
      extend1: Scoped,
      extendN: Scoped*
  ): TaskKey[T] =
    apply(AttributeKey[Task[T]](label, description, extendScoped(extend1, extendN)))

  def apply[T: Manifest](
      label: String,
      description: String,
      rank: Int,
      extend1: Scoped,
      extendN: Scoped*
  ): TaskKey[T] =
    apply(AttributeKey[Task[T]](label, description, extendScoped(extend1, extendN), rank))

  def apply[T](akey: AttributeKey[Task[T]]): TaskKey[T] = Scoped.scopedTask(Scope.ThisScope, akey)

  def local[T: Manifest]: TaskKey[T] = apply[T](AttributeKey.local[Task[T]])
}

/** Constructs SettingKeys, which are associated with a value to define a basic setting.*/
object SettingKey {
  def apply[T: Manifest: OptJsonWriter](
      label: String,
      description: String = "",
      rank: Int = KeyRanks.DefaultSettingRank
  ): SettingKey[T] =
    apply(AttributeKey[T](label, description, rank))

  def apply[T: Manifest: OptJsonWriter](
      label: String,
      description: String,
      extend1: Scoped,
      extendN: Scoped*
  ): SettingKey[T] =
    apply(AttributeKey[T](label, description, extendScoped(extend1, extendN)))

  def apply[T: Manifest: OptJsonWriter](
      label: String,
      description: String,
      rank: Int,
      extend1: Scoped,
      extendN: Scoped*
  ): SettingKey[T] =
    apply(AttributeKey[T](label, description, extendScoped(extend1, extendN), rank))

  def apply[T](akey: AttributeKey[T]): SettingKey[T] = Scoped.scopedSetting(Scope.ThisScope, akey)

  def local[T: Manifest: OptJsonWriter]: SettingKey[T] = apply[T](AttributeKey.local[T])
}
