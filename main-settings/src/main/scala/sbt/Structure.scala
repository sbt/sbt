/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import scala.language.experimental.macros

import sbt.internal.util.Types._
import sbt.internal.util.{ ~>, AList, AttributeKey, Settings, SourcePosition }
import sbt.util.OptJsonWriter
import sbt.ConcurrentRestrictions.Tag
import sbt.Def.{ Initialize, KeyedInitialize, ScopedKey, Setting, setting }
import std.TaskExtra.{ task => mktask, _ }

/** An abstraction on top of Settings for build configuration and task definition. */
sealed trait Scoped { def scope: Scope; val key: AttributeKey[_] }

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
  ): Setting[T] = {
    import TupleSyntax._
    set((this, other)(f), source)
  }

  final def withRank(rank: Int): SettingKey[T] =
    SettingKey(AttributeKey.copyWithRank(key, rank))
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
  ): Setting[Task[T]] = {
    import TupleSyntax._
    set((this, other)((a, b) => (a, b) map f.tupled), source)
  }

  final def withRank(rank: Int): TaskKey[T] =
    TaskKey(AttributeKey.copyWithRank(key, rank))
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
}

/** Methods and types related to constructing settings, including keys, scopes, and initializations. */
object Scoped {
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

    /** From the given [[Settings]], extract the value bound to this key. */
    final def get(settings: Settings[Scope]): Option[S] =
      settings.get(scopedKey.scope, scopedKey.key)

    /**
     * Creates an [[Def.Initialize]] with value [[scala.None]] if there was no previous definition of this key,
     * and `[[scala.Some]](value)` if a definition exists. Useful for when you want to use the ''existence'' of
     * one setting in order to define another setting.
     * @return currently bound value wrapped in `Initialize[Some[T]]`, or `Initialize[None]` if unbound.
     */
    final def ? : Initialize[Option[S]] = Def.optional(scopedKey)(idFun)

    /**
     * Creates an [[Def.Initialize]] with value bound to this key, or returns `i` parameter if unbound.
     * @param i value to return if this setting doesn't have a value.
     * @return currently bound setting value, or `i` if unbound.
     */
    final def or[T >: S](i: Initialize[T]): Initialize[T] = {
      import TupleSyntax._
      (this.?, i)(_ getOrElse _)
    }

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

    @deprecated("No longer needed with new task syntax and SettingKey inheriting from Initialize.",
                "0.13.2")
    def task: SettingKey[Task[S]] = scopedSetting(scope, key)

    def get(settings: Settings[Scope]): Option[Task[S]] = settings.get(scope, key)

    def ? : Initialize[Task[Option[S]]] = Def.optional(scopedKey) {
      case None => mktask { None }; case Some(t) => t map some.fn
    }

    def ??[T >: S](or: => T): Initialize[Task[T]] = Def.optional(scopedKey)(_ getOrElse mktask(or))

    def or[T >: S](i: Initialize[Task[T]]): Initialize[Task[T]] =
      (this.? zipWith i)((x, y) => (x, y) map { case (a, b) => a getOrElse b })
  }

  final class RichInitializeTask[S](i: Initialize[Task[S]]) extends RichInitTaskBase[S, Task] {
    protected def onTask[T](f: Task[S] => Task[T]): Initialize[Task[T]] = i apply f

    def dependsOn(tasks: AnyInitTask*): Initialize[Task[S]] = {
      import TupleSyntax._
      (i, Initialize.joinAny[Task](tasks))((thisTask, deps) => thisTask.dependsOn(deps: _*))
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
    ): Initialize[Task[S]] = {
      import TupleSyntax._
      (Initialize.joinAny[Task](tasks), i)((ts, i) => i.copy(info = i.info.set(key, ts)))
    }
  }

  final class RichInitializeInputTask[S](i: Initialize[InputTask[S]])
      extends RichInitTaskBase[S, InputTask] {
    protected def onTask[T](f: Task[S] => Task[T]): Initialize[InputTask[T]] = i(_ mapTask f)

    def dependsOn(tasks: AnyInitTask*): Initialize[InputTask[S]] = {
      import TupleSyntax._
      (i, Initialize.joinAny[Task](tasks))((thisTask, deps) =>
        thisTask.mapTask(_.dependsOn(deps: _*)))
    }
  }

  sealed abstract class RichInitTaskBase[S, R[_]] {
    protected def onTask[T](f: Task[S] => Task[T]): Initialize[R[T]]

    def flatMap[T](f: S => Task[T]): Initialize[R[T]] = flatMapR(f compose successM)
    def map[T](f: S => T): Initialize[R[T]] = mapR(f compose successM)
    def andFinally(fin: => Unit): Initialize[R[S]] = onTask(_ andFinally fin)
    def doFinally(t: Task[Unit]): Initialize[R[S]] = onTask(_ doFinally t)

    def ||[T >: S](alt: Task[T]): Initialize[R[T]] = onTask(_ || alt)
    def &&[T](alt: Task[T]): Initialize[R[T]] = onTask(_ && alt)

    def tag(tags: Tag*): Initialize[R[S]] = onTask(_.tag(tags: _*))
    def tagw(tags: (Tag, Int)*): Initialize[R[S]] = onTask(_.tagw(tags: _*))

    @deprecated(
      "Use the `result` method to create a task that returns the full Result of this task.  Then, call `flatMap` on the new task.",
      "0.13.0")
    def flatMapR[T](f: Result[S] => Task[T]): Initialize[R[T]] = onTask(_ flatMapR f)

    @deprecated(
      "Use the `result` method to create a task that returns the full Result of this task.  Then, call `map` on the new task.",
      "0.13.0")
    def mapR[T](f: Result[S] => T): Initialize[R[T]] = onTask(_ mapR f)

    @deprecated(
      "Use the `failure` method to create a task that returns Incomplete when this task fails and then call `flatMap` on the new task.",
      "0.13.0")
    def flatFailure[T](f: Incomplete => Task[T]): Initialize[R[T]] = flatMapR(f compose failM)

    @deprecated(
      "Use the `failure` method to create a task that returns Incomplete when this task fails and then call `map` on the new task.",
      "0.13.0")
    def mapFailure[T](f: Incomplete => T): Initialize[R[T]] = mapR(f compose failM)
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

  sealed abstract class RichTaskables[K[L[x]]](final val keys: K[ScopedTaskable])(
      implicit a: AList[K]
  ) {

    type App[T] = Initialize[Task[T]]
    type Fun[M[_], Ret]

    protected def convert[M[_], Ret](f: Fun[M, Ret]): K[M] => Ret

    private[this] val inputs: K[App] = a.transform(keys, λ[ScopedTaskable ~> App](_.toTask))

    private[this] def onTasks[T](f: K[Task] => Task[T]): App[T] =
      Def.app[λ[L[x] => K[(L ∙ Task)#l]], Task[T]](inputs)(f)(AList.asplit[K, Task](a))

    def flatMap[T](f: Fun[Id, Task[T]]): App[T] = onTasks(_.flatMap(convert(f)))
    def flatMapR[T](f: Fun[Result, Task[T]]): App[T] = onTasks(_.flatMapR(convert(f)))
    def map[T](f: Fun[Id, T]): App[T] = onTasks(_.mapR(convert(f) compose allM))
    def mapR[T](f: Fun[Result, T]): App[T] = onTasks(_.mapR(convert(f)))
    def flatFailure[T](f: Seq[Incomplete] => Task[T]): App[T] = onTasks(_ flatFailure f)
    def mapFailure[T](f: Seq[Incomplete] => T): App[T] = onTasks(_ mapFailure f)
  }

  // format: off

  type ST[X] = ScopedTaskable[X]
  final class RichTaskable2[A, B](t2: (ST[A], ST[B])) extends RichTaskables[AList.T2K[A, B]#l](t2)(AList.tuple2[A, B]) {
    type Fun[M[_], Ret] = (M[A], M[B]) => Ret
    def identityMap = map(mkTuple2)
    protected def convert[M[_], R](f: (M[A], M[B]) => R) = f.tupled
  }
  final class RichTaskable3[A, B, C](t3: (ST[A], ST[B], ST[C])) extends RichTaskables[AList.T3K[A, B, C]#l](t3)(AList.tuple3[A, B, C]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C]) => Ret
    def identityMap = map(mkTuple3)
    protected def convert[M[_], R](f: Fun[M, R]) = f.tupled
  }
  final class RichTaskable4[A, B, C, D](t4: (ST[A], ST[B], ST[C], ST[D])) extends RichTaskables[AList.T4K[A, B, C, D]#l](t4)(AList.tuple4[A, B, C, D]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C], M[D]) => Ret
    def identityMap = map(mkTuple4)
    protected def convert[M[_], R](f: Fun[M, R]) = f.tupled
  }
  final class RichTaskable5[A, B, C, D, E](t5: (ST[A], ST[B], ST[C], ST[D], ST[E])) extends RichTaskables[AList.T5K[A, B, C, D, E]#l](t5)(AList.tuple5[A, B, C, D, E]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C], M[D], M[E]) => Ret
    def identityMap = map(mkTuple5)
    protected def convert[M[_], R](f: Fun[M, R]) = f.tupled
  }
  final class RichTaskable6[A, B, C, D, E, F](t6: (ST[A], ST[B], ST[C], ST[D], ST[E], ST[F])) extends RichTaskables[AList.T6K[A, B, C, D, E, F]#l](t6)(AList.tuple6[A, B, C, D, E, F]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C], M[D], M[E], M[F]) => Ret
    def identityMap = map(mkTuple6)
    protected def convert[M[_], R](z: Fun[M, R]) = z.tupled
  }
  final class RichTaskable7[A, B, C, D, E, F, G](t7: (ST[A], ST[B], ST[C], ST[D], ST[E], ST[F], ST[G])) extends RichTaskables[AList.T7K[A, B, C, D, E, F, G]#l](t7)(AList.tuple7[A, B, C, D, E, F, G]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G]) => Ret
    def identityMap = map(mkTuple7)
    protected def convert[M[_], R](z: Fun[M, R]) = z.tupled
  }
  final class RichTaskable8[A, B, C, D, E, F, G, H](t8: (ST[A], ST[B], ST[C], ST[D], ST[E], ST[F], ST[G], ST[H])) extends RichTaskables[AList.T8K[A, B, C, D, E, F, G, H]#l](t8)(AList.tuple8[A, B, C, D, E, F, G, H]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H]) => Ret
    def identityMap = map(mkTuple8)
    protected def convert[M[_], R](z: Fun[M, R]) = z.tupled
  }
  final class RichTaskable9[A, B, C, D, E, F, G, H, I](t9: (ST[A], ST[B], ST[C], ST[D], ST[E], ST[F], ST[G], ST[H], ST[I])) extends RichTaskables[AList.T9K[A, B, C, D, E, F, G, H, I]#l](t9)(AList.tuple9[A, B, C, D, E, F, G, H, I]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I]) => Ret
    def identityMap = map(mkTuple9)
    protected def convert[M[_], R](z: Fun[M, R]) = z.tupled
  }
  final class RichTaskable10[A, B, C, D, E, F, G, H, I, J](t10: ((ST[A], ST[B], ST[C], ST[D], ST[E], ST[F], ST[G], ST[H], ST[I], ST[J]))) extends RichTaskables[AList.T10K[A, B, C, D, E, F, G, H, I, J]#l](t10)(AList.tuple10[A, B, C, D, E, F, G, H, I, J]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J]) => Ret
    def identityMap = map(mkTuple10)
    protected def convert[M[_], R](z: Fun[M, R]) = z.tupled
  }
  final class RichTaskable11[A, B, C, D, E, F, G, H, I, J, K](t11: ((ST[A], ST[B], ST[C], ST[D], ST[E], ST[F], ST[G], ST[H], ST[I], ST[J], ST[K]))) extends RichTaskables[AList.T11K[A, B, C, D, E, F, G, H, I, J, K]#l](t11)(AList.tuple11[A, B, C, D, E, F, G, H, I, J, K]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J], M[K]) => Ret
    def identityMap = map(mkTuple11)
    protected def convert[M[_], R](z: Fun[M, R]) = z.tupled
  }

  def mkTuple2[A, B] = (a: A, b: B) => (a, b)
  def mkTuple3[A, B, C] = (a: A, b: B, c: C) => (a, b, c)
  def mkTuple4[A, B, C, D] = (a: A, b: B, c: C, d: D) => (a, b, c, d)
  def mkTuple5[A, B, C, D, E] = (a: A, b: B, c: C, d: D, e: E) => (a, b, c, d, e)
  def mkTuple6[A, B, C, D, E, F] = (a: A, b: B, c: C, d: D, e: E, f: F) => (a, b, c, d, e, f)
  def mkTuple7[A, B, C, D, E, F, G] = (a: A, b: B, c: C, d: D, e: E, f: F, g: G) => (a, b, c, d, e, f, g)
  def mkTuple8[A, B, C, D, E, F, G, H] = (a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H) => (a, b, c, d, e, f, g, h)
  def mkTuple9[A, B, C, D, E, F, G, H, I] = (a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I) => (a, b, c, d, e, f, g, h, i)
  def mkTuple10[A, B, C, D, E, F, G, H, I, J] = (a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J) => (a, b, c, d, e, f, g, h, i, j)
  def mkTuple11[A, B, C, D, E, F, G, H, I, J, K] = (a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K) => (a, b, c, d, e, f, g, h, i, j, k)
  def mkTuple12[A, B, C, D, E, F, G, H, I, J, K, L] = (a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L) => (a, b, c, d, e, f, g, h, i, j, k, l)
  def mkTuple13[A, B, C, D, E, F, G, H, I, J, K, L, N] = (a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, n: N) => (a, b, c, d, e, f, g, h, i, j, k, l, n)
  def mkTuple14[A, B, C, D, E, F, G, H, I, J, K, L, N, O] = (a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, n: N, o: O) => (a, b, c, d, e, f, g, h, i, j, k, l, n, o)
  def mkTuple15[A, B, C, D, E, F, G, H, I, J, K, L, N, O, P] = (a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J, k: K, l: L, n: N, o: O, p: P) => (a, b, c, d, e, f, g, h, i, j, k, l, n, o, p)

  final class Apply2[A, B](t2: (Initialize[A], Initialize[B])) {
    def apply[T](z: (A, B) => T) = Def.app[AList.T2K[A, B]#l, T](t2)(z.tupled)(AList.tuple2[A, B])
    def identity = apply(mkTuple2)
  }
  final class Apply3[A, B, C](t3: (Initialize[A], Initialize[B], Initialize[C])) {
    def apply[T](z: (A, B, C) => T) = Def.app[AList.T3K[A, B, C]#l, T](t3)(z.tupled)(AList.tuple3[A, B, C])
    def identity = apply(mkTuple3)
  }
  final class Apply4[A, B, C, D](t4: (Initialize[A], Initialize[B], Initialize[C], Initialize[D])) {
    def apply[T](z: (A, B, C, D) => T) = Def.app[AList.T4K[A, B, C, D]#l, T](t4)(z.tupled)(AList.tuple4[A, B, C, D])
    def identity = apply(mkTuple4)
  }
  final class Apply5[A, B, C, D, E](t5: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E])) {
    def apply[T](z: (A, B, C, D, E) => T) = Def.app[AList.T5K[A, B, C, D, E]#l, T](t5)(z.tupled)(AList.tuple5[A, B, C, D, E])
    def identity = apply(mkTuple5)
  }
  final class Apply6[A, B, C, D, E, F](t6: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F])) {
    def apply[T](z: (A, B, C, D, E, F) => T) = Def.app[AList.T6K[A, B, C, D, E, F]#l, T](t6)(z.tupled)(AList.tuple6[A, B, C, D, E, F])
    def identity = apply(mkTuple6)
  }
  final class Apply7[A, B, C, D, E, F, G](t7: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G])) {
    def apply[T](z: (A, B, C, D, E, F, G) => T) = Def.app[AList.T7K[A, B, C, D, E, F, G]#l, T](t7)(z.tupled)(AList.tuple7[A, B, C, D, E, F, G])
    def identity = apply(mkTuple7)
  }
  final class Apply8[A, B, C, D, E, F, G, H](t8: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H])) {
    def apply[T](z: (A, B, C, D, E, F, G, H) => T) = Def.app[AList.T8K[A, B, C, D, E, F, G, H]#l, T](t8)(z.tupled)(AList.tuple8[A, B, C, D, E, F, G, H])
    def identity = apply(mkTuple8)
  }
  final class Apply9[A, B, C, D, E, F, G, H, I](t9: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I])) {
    def apply[T](z: (A, B, C, D, E, F, G, H, I) => T) = Def.app[AList.T9K[A, B, C, D, E, F, G, H, I]#l, T](t9)(z.tupled)(AList.tuple9[A, B, C, D, E, F, G, H, I])
    def identity = apply(mkTuple9)
  }
  final class Apply10[A, B, C, D, E, F, G, H, I, J](t10: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J])) {
    def apply[T](z: (A, B, C, D, E, F, G, H, I, J) => T) = Def.app[AList.T10K[A, B, C, D, E, F, G, H, I, J]#l, T](t10)(z.tupled)(AList.tuple10[A, B, C, D, E, F, G, H, I, J])
    def identity = apply(mkTuple10)
  }
  final class Apply11[A, B, C, D, E, F, G, H, I, J, K](t11: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J], Initialize[K])) {
    def apply[T](z: (A, B, C, D, E, F, G, H, I, J, K) => T) = Def.app[AList.T11K[A, B, C, D, E, F, G, H, I, J, K]#l, T](t11)(z.tupled)(AList.tuple11[A, B, C, D, E, F, G, H, I, J, K])
    def identity = apply(mkTuple11)
  }

  // format: on

  private[sbt] def extendScoped(s1: Scoped, ss: Seq[Scoped]): Seq[AttributeKey[_]] =
    s1.key +: ss.map(_.key)
}

/** The sbt 0.10 style DSL was deprecated in 0.13.13, favouring the use of the '.value' macro.
 *
 * See http://www.scala-sbt.org/0.13/docs/Migrating-from-sbt-012x.html for how to migrate.
 */
trait TupleSyntax {
  import Scoped._

  // format: off

  // this is the least painful arrangement I came up with
  implicit def t2ToTable2[A, B](t2: (ScopedTaskable[A], ScopedTaskable[B])): RichTaskable2[A, B] = new RichTaskable2(t2)
  implicit def t3ToTable3[A, B, C](t3: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C])): RichTaskable3[A, B, C] = new RichTaskable3(t3)
  implicit def t4ToTable4[A, B, C, D](t4: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D])): RichTaskable4[A, B, C, D] = new RichTaskable4(t4)
  implicit def t5ToTable5[A, B, C, D, E](t5: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E])): RichTaskable5[A, B, C, D, E] = new RichTaskable5(t5)
  implicit def t6ToTable6[A, B, C, D, E, F](t6: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F])): RichTaskable6[A, B, C, D, E, F] = new RichTaskable6(t6)
  implicit def t7ToTable7[A, B, C, D, E, F, G](t7: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G])): RichTaskable7[A, B, C, D, E, F, G] = new RichTaskable7(t7)
  implicit def t8ToTable8[A, B, C, D, E, F, G, H](t8: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H])): RichTaskable8[A, B, C, D, E, F, G, H] = new RichTaskable8(t8)
  implicit def t9ToTable9[A, B, C, D, E, F, G, H, I](t9: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I])): RichTaskable9[A, B, C, D, E, F, G, H, I] = new RichTaskable9(t9)
  implicit def t10ToTable10[A, B, C, D, E, F, G, H, I, J](t10: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I], ScopedTaskable[J])): RichTaskable10[A, B, C, D, E, F, G, H, I, J] = new RichTaskable10(t10)
  implicit def t11ToTable11[A, B, C, D, E, F, G, H, I, J, K](t11: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I], ScopedTaskable[J], ScopedTaskable[K])): RichTaskable11[A, B, C, D, E, F, G, H, I, J, K] = new RichTaskable11(t11)

  implicit def t2ToApp2[A, B](t2: (Initialize[A], Initialize[B])): Apply2[A, B] = new Apply2(t2)
  implicit def t3ToApp3[A, B, C](t3: (Initialize[A], Initialize[B], Initialize[C])): Apply3[A, B, C] = new Apply3(t3)
  implicit def t4ToApp4[A, B, C, D](t4: (Initialize[A], Initialize[B], Initialize[C], Initialize[D])): Apply4[A, B, C, D] = new Apply4(t4)
  implicit def t5ToApp5[A, B, C, D, E](t5: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E])): Apply5[A, B, C, D, E] = new Apply5(t5)
  implicit def t6ToApp6[A, B, C, D, E, F](t6: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F])): Apply6[A, B, C, D, E, F] = new Apply6(t6)
  implicit def t7ToApp7[A, B, C, D, E, F, G](t7: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G])): Apply7[A, B, C, D, E, F, G] = new Apply7(t7)
  implicit def t8ToApp8[A, B, C, D, E, F, G, H](t8: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H])): Apply8[A, B, C, D, E, F, G, H] = new Apply8(t8)
  implicit def t9ToApp9[A, B, C, D, E, F, G, H, I](t9: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I])): Apply9[A, B, C, D, E, F, G, H, I] = new Apply9(t9)
  implicit def t10ToApp10[A, B, C, D, E, F, G, H, I, J](t10: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J])): Apply10[A, B, C, D, E, F, G, H, I, J] = new Apply10(t10)
  implicit def t11ToApp11[A, B, C, D, E, F, G, H, I, J, K](t11: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J], Initialize[K])): Apply11[A, B, C, D, E, F, G, H, I, J, K] = new Apply11(t11)

  // format: on
}

object TupleSyntax extends TupleSyntax

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
    new InputKey[T] { val key = akey; def scope = Scope.ThisScope }
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

  def apply[T](akey: AttributeKey[Task[T]]): TaskKey[T] =
    new TaskKey[T] { val key = akey; def scope = Scope.ThisScope }

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

  def apply[T](akey: AttributeKey[T]): SettingKey[T] =
    new SettingKey[T] { val key = akey; def scope = Scope.ThisScope }

  def local[T: Manifest: OptJsonWriter]: SettingKey[T] = apply[T](AttributeKey.local[T])
}
