/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import scala.annotation.targetName

import sbt.internal.util.Types._
import sbt.internal.util.{ ~>, AList, AttributeKey, Settings, SourcePosition }
import sbt.util.OptJsonWriter
import sbt.ConcurrentRestrictions.Tag
import sbt.Def.{ Initialize, ScopedKey, Setting, setting }
import std.TaskMacro
import std.TaskExtra.{ task => mktask, _ }
import scala.reflect.{ ClassTag, ManifestFactory }

/** An abstraction on top of Settings for build configuration and task definition. */
sealed trait Scoped extends Equals:
  def scope: Scope
  val key: AttributeKey[_]

  override def equals(that: Any): Boolean =
    (this eq that.asInstanceOf[AnyRef]) || (that match {
      case that: Scoped => scope == that.scope && key == that.key && canEqual(that)
      case _            => false
    })

  override def hashCode(): Int = (scope, key).##
end Scoped

/** A SettingKey, TaskKey or `Initialize[Task]` that can be converted into an `Initialize[Task]`. */
sealed trait Taskable[A]:
  def toTask: Initialize[Task[A]]
end Taskable

sealed trait TaskableImplicits { self: Taskable.type =>
  implicit def fromInit[A](x: Initialize[A]): Taskable[A] =
    new Taskable[A] { def toTask = Def.toITask(x) }
}

object Taskable extends TaskableImplicits {
  implicit def fromITask[A](x: Initialize[Task[A]]): Taskable[A] =
    new Taskable[A] { def toTask = x }
}

/** A common type for SettingKey and TaskKey so that both can be used as inputs to tasks. */
sealed trait ScopedTaskable[A] extends Scoped with Taskable[A]

/**
 * Identifies a setting.  It consists of three parts: the scope, the name, and the type of a value associated with this key.
 * The scope is represented by a value of type Scope.
 * The name and the type are represented by a value of type `AttributeKey[T]`.
 * Instances are constructed using the companion object.
 */
sealed abstract class SettingKey[A1]
    extends ScopedTaskable[A1]
    with Def.KeyedInitialize[A1]
    with Scoped.ScopingSetting[SettingKey[A1]]
    with Scoped.DefinableSetting[A1]:

  val key: AttributeKey[A1]

  override def toString: String = s"SettingKey($scope / $key)"

  final def toTask: Initialize[Task[A1]] = this apply inlineTask

  final def scopedKey: ScopedKey[A1] = ScopedKey(scope, key)

  // @deprecated(Scope.inIsDeprecated, "1.5.0")
  final def in(scope: Scope): SettingKey[A1] =
    Scoped.scopedSetting(Scope.replaceThis(this.scope)(scope), this.key)

  /** Internal function for the setting macro. */
  inline def settingMacro[A](inline a: A): Initialize[A] =
    ${ std.SettingMacro.settingMacroImpl[A]('a) }

  final inline def :=(inline v: A1): Setting[A1] =
    ${ TaskMacro.settingAssignMacroImpl('this, 'v) }

  final inline def +=[A2](inline v: A2)(using Append.Value[A1, A2]): Setting[A1] =
    ${ TaskMacro.settingAppend1Impl[A1, A2]('this, 'v) }

  final inline def append1[A2](v: Initialize[A2])(using
      a: Append.Value[A1, A2]
  ): Setting[A1] = make(v)(a.appendValue)

  final inline def ++=[A2](inline vs: A2)(using Append.Values[A1, A2]): Setting[A1] =
    appendN(settingMacro[A2](vs))

  final def appendN[V](vs: Initialize[V])(using
      ev: Append.Values[A1, V]
  ): Setting[A1] = make(vs)(ev.appendValues)

  final inline def <+=[A2](inline v: Initialize[A2]): Setting[A1] =
    ${ TaskMacro.fakeSettingAppend1Position[A1, A2]('v) }

  final inline def <++=[A2](inline vs: Initialize[A2]): Setting[A1] =
    ${ TaskMacro.fakeSettingAppendNPosition[A1, A2]('vs) }

  final inline def -=[A2](inline v: A2)(using Remove.Value[A1, A2]): Setting[A1] =
    remove1(settingMacro[A2](v))

  final inline def remove1[V](v: Initialize[V])(using
      ev: Remove.Value[A1, V]
  ): Setting[A1] = make(v)(ev.removeValue)

  final inline def --=[A2](inline vs: A2)(using Remove.Values[A1, A2]): Setting[A1] =
    removeN(settingMacro[A2](vs))

  final inline def removeN[V](vs: Initialize[V])(using
      ev: Remove.Values[A1, V]
  ): Setting[A1] = make(vs)(ev.removeValues)

  final inline def ~=(f: A1 => A1): Setting[A1] = transform(f)

  final inline def transform(f: A1 => A1): Setting[A1] = set(scopedKey(f))

  inline def make[A2](other: Initialize[A2])(f: (A1, A2) => A1): Setting[A1] =
    set(this.zipWith(other)(f))

  protected[this] inline def make[A2](other: Initialize[A2], source: SourcePosition)(
      f: (A1, A2) => A1
  ): Setting[A1] = set0(this.zipWith(other)(f), source)

  final def withRank(rank: Int): SettingKey[A1] =
    SettingKey(AttributeKey.copyWithRank(key, rank))

  def canEqual(that: Any): Boolean = that.isInstanceOf[SettingKey[_]]
end SettingKey

/**
 * Identifies a task.  It consists of three parts: the scope, the name, and the type of the value computed by a task associated with this key.
 * The scope is represented by a value of type Scope.
 * The name and the type are represented by a value of type `AttributeKey[Task[T]]`.
 * Instances are constructed using the companion object.
 */
sealed abstract class TaskKey[A1]
    extends ScopedTaskable[A1]
    with Def.KeyedInitialize[Task[A1]]
    with Scoped.ScopingSetting[TaskKey[A1]]
    with Scoped.DefinableTask[A1]:

  val key: AttributeKey[Task[A1]]

  override def toString: String = s"TaskKey($scope / $key)"

  def toTask: Initialize[Task[A1]] = this

  def scopedKey: ScopedKey[Task[A1]] = ScopedKey(scope, key)

  // @deprecated(Scope.inIsDeprecated, "1.5.0")
  def in(scope: Scope): TaskKey[A1] =
    Scoped.scopedTask(Scope.replaceThis(this.scope)(scope), this.key)

  inline def +=[A2](inline v: A2)(using Append.Value[A1, A2]): Setting[Task[A1]] =
    append1[A2](taskMacro(v))

  inline def append1[A2](v: Initialize[Task[A2]])(using
      ev: Append.Value[A1, A2]
  ): Setting[Task[A1]] =
    make(v)(ev.appendValue)

  inline def ++=[A2](inline vs: A2)(using Append.Values[A1, A2]): Setting[Task[A1]] =
    appendN(taskMacro[A2](vs))

  inline def appendN[A2](vs: Initialize[Task[A2]])(using
      ev: Append.Values[A1, A2]
  ): Setting[Task[A1]] = make(vs)(ev.appendValues)

  inline def <+=[A2](inline v: Initialize[Task[A2]]): Setting[Task[A1]] =
    ${ TaskMacro.fakeTaskAppend1Position[A1, A2]('v) }

  inline def <++=[A2](inline vs: Initialize[Task[A2]]): Setting[Task[A1]] =
    ${ TaskMacro.fakeTaskAppendNPosition[A1, A2]('vs) }

  final inline def -=[A2](v: A2)(using Remove.Value[A1, A2]): Setting[Task[A1]] =
    remove1[A2](taskMacro[A2](v))

  final inline def remove1[A2](v: Initialize[Task[A2]])(using
      ev: Remove.Value[A1, A2]
  ): Setting[Task[A1]] = make(v)(ev.removeValue)

  final inline def --=[A2](vs: A2)(using r: Remove.Values[A1, A2]): Setting[Task[A1]] =
    removeN[A2](taskMacro[A2](vs))

  final inline def removeN[A2](vs: Initialize[Task[A2]])(using
      ev: Remove.Values[A1, A2]
  ): Setting[Task[A1]] = make(vs)(ev.removeValues)

  inline def make[A2](other: Initialize[Task[A2]], source: SourcePosition)(
      f: (A1, A2) => A1
  ): Setting[Task[A1]] =
    set0(
      this.zipWith(other) { (ta1: Task[A1], ta2: Task[A2]) =>
        multT2Task((ta1, ta2)).mapN(f.tupled)
      },
      source
    )

  inline def make[A2](other: Initialize[Task[A2]])(
      f: (A1, A2) => A1
  ): Setting[Task[A1]] =
    set(this.zipWith(other) { (ta1: Task[A1], ta2: Task[A2]) =>
      multT2Task((ta1, ta2)).mapN(f.tupled)
    })

  final def withRank(rank: Int): TaskKey[A1] =
    TaskKey(AttributeKey.copyWithRank(key, rank))

  def canEqual(that: Any): Boolean = that.isInstanceOf[TaskKey[_]]
end TaskKey

/**
 * Identifies an input task.  An input task parses input and produces a task to run.
 * It consists of three parts: the scope, the name, and the type of the value produced by an input task associated with this key.
 * The scope is represented by a value of type Scope.
 * The name and the type are represented by a value of type `AttributeKey[InputTask[T]]`.
 * Instances are constructed using the companion object.
 */
sealed trait InputKey[A1]
    extends Scoped
    with Def.KeyedInitialize[InputTask[A1]]
    with Scoped.ScopingSetting[InputKey[A1]]
    with Scoped.DefinableSetting[InputTask[A1]]:

  val key: AttributeKey[InputTask[A1]]

  override def toString: String = s"InputKey($scope / $key)"

  def scopedKey: ScopedKey[InputTask[A1]] = ScopedKey(scope, key)

  // @deprecated(Scope.inIsDeprecated, "1.5.0")
  def in(scope: Scope): InputKey[A1] =
    Scoped.scopedInput(Scope.replaceThis(this.scope)(scope), this.key)

  private inline def inputTaskMacro[A2](inline a: A2): Def.Initialize[InputTask[A2]] =
    ${ std.InputTaskMacro.inputTaskMacroImpl('a) }

  inline def :=(inline a: A1): Setting[InputTask[A1]] =
    set(inputTaskMacro[A1](a))

  final inline def ~=(f: A1 => A1): Setting[InputTask[A1]] = transform(f)

  final inline def transform(f: A1 => A1): Setting[InputTask[A1]] =
    set(scopedKey(_ mapTask { _ map f }))

  final def withRank(rank: Int): InputKey[A1] =
    InputKey(AttributeKey.copyWithRank(key, rank))

  def canEqual(that: Any): Boolean = that.isInstanceOf[InputKey[_]]
end InputKey

/** Methods and types related to constructing settings, including keys, scopes, and initializations. */
object Scoped:
  implicit def taskScopedToKey[T](s: TaskKey[T]): ScopedKey[Task[T]] = ScopedKey(s.scope, s.key)

  implicit def inputScopedToKey[T](s: InputKey[T]): ScopedKey[InputTask[T]] =
    ScopedKey(s.scope, s.key)

  private[sbt] def coerceTag[A1: ClassTag]: Manifest[A1] =
    summon[ClassTag[A1]] match
      case mf: Manifest[A1] => mf
      case tag              => ManifestFactory.classType[A1](tag.runtimeClass)

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
   */
  sealed trait ScopingSetting[ResultType]:
    private[sbt] def in(s: Scope): ResultType
  // @deprecated(Scope.inIsDeprecated, "1.5.0")
  // def in(s: Scope): ResultType

  // @deprecated(Scope.inIsDeprecated, "1.5.0")
  // def in(p: Reference): ResultType = in(Select(p), This, This)

  // @deprecated(Scope.inIsDeprecated, "1.5.0")
  // def in(t: Scoped): ResultType = in(This, This, Select(t.key))

  // @deprecated(Scope.inIsDeprecated, "1.5.0")
  // def in(c: ConfigKey): ResultType = in(This, Select(c), This)

  // @deprecated(Scope.inIsDeprecated, "1.5.0")
  // def in(c: ConfigKey, t: Scoped): ResultType = in(This, Select(c), Select(t.key))

  // @deprecated(Scope.inIsDeprecated, "1.5.0")
  // def in(p: Reference, c: ConfigKey): ResultType = in(Select(p), Select(c), This)

  // @deprecated(Scope.inIsDeprecated, "1.5.0")
  // def in(p: Reference, t: Scoped): ResultType = in(Select(p), This, Select(t.key))

  // @deprecated(Scope.inIsDeprecated, "1.5.0")
  // def in(p: Reference, c: ConfigKey, t: Scoped): ResultType =
  //   in(Select(p), Select(c), Select(t.key))

  // @deprecated(Scope.inIsDeprecated, "1.5.0")
  // def in(
  //     p: ScopeAxis[Reference],
  //     c: ScopeAxis[ConfigKey],
  //     t: ScopeAxis[AttributeKey[_]]
  // ): ResultType = in(Scope(p, c, t, This))
  end ScopingSetting

  def scopedSetting[T](s: Scope, k: AttributeKey[T]): SettingKey[T] =
    new SettingKey[T] { val scope = s; val key = k }

  def scopedInput[T](s: Scope, k: AttributeKey[InputTask[T]]): InputKey[T] =
    new InputKey[T] { val scope = s; val key = k }

  def scopedTask[T](s: Scope, k: AttributeKey[Task[T]]): TaskKey[T] =
    new TaskKey[T] { val scope = s; val key = k }

  /**
   * Mixin trait for adding convenience vocabulary associated with applying a setting to a configuration item.
   */
  sealed trait DefinableSetting[A1] { self =>
    def scopedKey: ScopedKey[A1]

    private[sbt] final inline def :==(inline app: A1): Setting[A1] =
      set(Def.valueStrict(app))

    inline def <<=(inline app: Initialize[A1]): Setting[A1] =
      ${ TaskMacro.fakeSettingAssignImpl('app) }

    /** In addition to creating Def.setting(...), this captures the source position. */
    inline def set(inline app: Initialize[A1]): Setting[A1] =
      ${ TaskMacro.settingSetImpl('self, 'app) }

    /** Internally used function for setting a value along with the `.sbt` file location where it is defined. */
    final def set0(app: Initialize[A1], source: SourcePosition): Setting[A1] =
      setting(scopedKey, app, source)

    /** From the given `Settings`, extract the value bound to this key. */
    final def get(settings: Settings[Scope]): Option[A1] =
      settings.get(scopedKey.scope, scopedKey.key)

    /**
     * Creates an [[Def.Initialize]] with value `scala.None` if there was no previous definition of this key,
     * and `scala.Some(value)` if a definition exists. Useful for when you want to use the ''existence'' of
     * one setting in order to define another setting.
     * @return currently bound value wrapped in `Initialize[Some[T]]`, or `Initialize[None]` if unbound.
     */
    final def ? : Initialize[Option[A1]] = Def.optional(scopedKey)(idFun)

    /**
     * Creates an [[Def.Initialize]] with value bound to this key, or returns `i` parameter if unbound.
     * @param i value to return if this setting doesn't have a value.
     * @return currently bound setting value, or `i` if unbound.
     */
    final def or[T >: A1](i: Initialize[T]): Initialize[T] = ?.zipWith(i)(_.getOrElse(_))

    /**
     * Like [[?]], but with a call-by-name parameter rather than an existing [[Def.Initialize]].
     * Useful when you want to have a value computed when no value is bound to this key.
     * @param or by-name expression evaluated when a value is needed.
     * @return currently bound setting value, or the result of `or` if unbound.
     */
    final def ??[T >: A1](or: => T): Initialize[T] = Def.optional(scopedKey)(_ getOrElse or)
  }

  private[sbt] trait Syntax:
    
    // format: off
    // richInitialize
    extension [A1](init: Initialize[A1])
      @targetName("mapTaskInitialize")
      def map[A2](f: A1 => A2): Initialize[Task[A2]] = init(s => mktask(f(s)))

      @targetName("flatMapValueInitialize")
      def flatMapTaskValue[A2](f: A1 => Task[A2]): Initialize[Task[A2]] = init(f)
    // format: on

    // richInitializeTask
    extension [A1](init: Initialize[Task[A1]])
      protected def onTask[A2](f: Task[A1] => Task[A2]): Initialize[Task[A2]] =
        init.apply(f)

      def flatMapTaskValue[T](f: A1 => Task[T]): Initialize[Task[T]] =
        onTask(_.result flatMap (f compose successM))
      def map[A2](f: A1 => A2): Initialize[Task[A2]] =
        onTask(_.result map (f compose successM))
      def andFinally(fin: => Unit): Initialize[Task[A1]] =
        onTask(_ andFinally fin)
      def doFinally(t: Task[Unit]): Initialize[Task[A1]] =
        onTask(_ doFinally t)
      def ||[T >: A1](alt: Task[T]): Initialize[Task[T]] = onTask(_ || alt)
      def &&[T](alt: Task[T]): Initialize[Task[T]] = onTask(_ && alt)
      def tag(tags: Tag*): Initialize[Task[A1]] = onTask(_.tag(tags: _*))
      def tagw(tags: (Tag, Int)*): Initialize[Task[A1]] = onTask(_.tagw(tags: _*))

      // Task-specific extensions
      def dependsOnTask[A2](task1: Initialize[Task[A2]]): Initialize[Task[A1]] =
        dependsOnSeq(Seq[AnyInitTask](task1.asInstanceOf[AnyInitTask]))
      def dependsOnSeq(tasks: Seq[AnyInitTask]): Initialize[Task[A1]] =
        init.zipWith(
          Initialize.joinAny[Task](coerceToAnyTaskSeq(tasks))
        )((thisTask, deps) => thisTask.dependsOn(deps: _*))
      def failure: Initialize[Task[Incomplete]] = init(_.failure)
      def result: Initialize[Task[Result[A1]]] = init(_.result)
      def xtriggeredBy[A2](tasks: Initialize[Task[A2]]*): Initialize[Task[A1]] =
        nonLocal(tasks.toSeq.asInstanceOf[Seq[AnyInitTask]], Def.triggeredBy)
      def triggeredBy[A2](tasks: Initialize[Task[A2]]*): Initialize[Task[A1]] =
        nonLocal(tasks.toSeq.asInstanceOf[Seq[AnyInitTask]], Def.triggeredBy)
      def runBefore[A2](tasks: Initialize[Task[A2]]*): Initialize[Task[A1]] =
        nonLocal(tasks.toSeq.asInstanceOf[Seq[AnyInitTask]], Def.runBefore)
      private[this] def nonLocal(
          tasks: Seq[AnyInitTask],
          key: AttributeKey[Seq[Task[_]]]
      ): Initialize[Task[A1]] =
        Initialize
          .joinAny[Task](coerceToAnyTaskSeq(tasks))
          .zipWith(init)((ts, i) => i.copy(info = i.info.set(key, ts)))

    extension [A1](init: Initialize[InputTask[A1]])
      @targetName("onTaskInitializeInputTask")
      protected def onTask[T](f: Task[A1] => Task[T]): Initialize[InputTask[T]] =
        init(_ mapTask f)

      @targetName("flatMapTaskValueInitializeInputTask")
      def flatMapTaskValue[T](f: A1 => Task[T]): Initialize[InputTask[T]] =
        onTask(_.result flatMap (f compose successM))
      @targetName("mapInitializeInputTask")
      def map[A2](f: A1 => A2): Initialize[InputTask[A2]] =
        onTask(_.result map (f compose successM))
      @targetName("andFinallyInitializeInputTask")
      def andFinally(fin: => Unit): Initialize[InputTask[A1]] = onTask(_ andFinally fin)
      @targetName("doFinallyInitializeInputTask")
      def doFinally(t: Task[Unit]): Initialize[InputTask[A1]] = onTask(_ doFinally t)
      @targetName("||_InitializeInputTask")
      def ||[T >: A1](alt: Task[T]): Initialize[InputTask[T]] = onTask(_ || alt)
      @targetName("&&_InitializeInputTask")
      def &&[T](alt: Task[T]): Initialize[InputTask[T]] = onTask(_ && alt)
      @targetName("tagInitializeInputTask")
      def tag(tags: Tag*): Initialize[InputTask[A1]] = onTask(_.tag(tags: _*))
      @targetName("tagwInitializeInputTask")
      def tagw(tags: (Tag, Int)*): Initialize[InputTask[A1]] = onTask(_.tagw(tags: _*))

      // InputTask specific extensions
      @targetName("dependsOnTaskInitializeInputTask")
      def dependsOnTask[B1](task1: Initialize[Task[B1]]): Initialize[InputTask[A1]] =
        dependsOnSeq(Seq[AnyInitTask](task1.asInstanceOf[AnyInitTask]))
      @targetName("dependsOnSeqInitializeInputTask")
      def dependsOnSeq(tasks: Seq[AnyInitTask]): Initialize[InputTask[A1]] =
        init.zipWith(Initialize.joinAny[Task](coerceToAnyTaskSeq(tasks)))((thisTask, deps) =>
          thisTask.mapTask(_.dependsOn(deps: _*))
        )
  end Syntax

  // Duplicated with ProjectExtra.
  private[sbt] object syntax extends Syntax

  sealed trait DefinableTask[A1] { self: TaskKey[A1] =>

    /** Internal function for the task macro. */
    inline def taskMacro[A](inline a: A): Initialize[Task[A]] =
      ${ TaskMacro.taskMacroImpl[A]('a, cached = false) }

    private[sbt] inline def :==(app: A1): Setting[Task[A1]] =
      set(Def.valueStrict(std.TaskExtra.constant(app)))

    private[sbt] inline def ::=(app: Task[A1]): Setting[Task[A1]] =
      set(Def.valueStrict(app))

    inline def :=(inline a: A1): Setting[Task[A1]] =
      set(taskMacro(a))

    inline def <<=(inline app: Initialize[Task[A1]]): Setting[Task[A1]] =
      ${ TaskMacro.fakeItaskAssignPosition[A1]('app) }

    /** In addition to creating Def.setting(...), this captures the source position. */
    inline def set(inline app: Initialize[Task[A1]]): Setting[Task[A1]] =
      ${ std.DefinableTaskMacro.taskSetImpl('self, 'app) }

    private[sbt] def set0(app: Initialize[Task[A1]], source: SourcePosition): Setting[Task[A1]] =
      Def.setting(scopedKey, app, source)

    inline def ~=(inline f: A1 => A1): Setting[Task[A1]] = transform(f)

    inline def transform(f: A1 => A1): Setting[Task[A1]] = set(scopedKey(_ map f))

    // @deprecated(
    //   "No longer needed with new task syntax and SettingKey inheriting from Initialize.",
    //   "0.13.2"
    // )
    // def task: SettingKey[Task[A1]] = scopedSetting(scope, key)

    def toSettingKey: SettingKey[Task[A1]] = scopedSetting(scope, key)

    def get(settings: Settings[Scope]): Option[Task[A1]] = settings.get(scope, key)

    def ? : Initialize[Task[Option[A1]]] = Def.optional(scopedKey) {
      case None    => mktask { None }
      case Some(t) => t map some[A1]
    }

    def ??[T >: A1](or: => T): Initialize[Task[T]] = Def.optional(scopedKey)(_ getOrElse mktask(or))

    // def or[A2 >: A1](i: Initialize[Task[A2]]): Initialize[Task[A2]] =
    //   this.?.zipWith(i) { (toa1: Task[Option[A1]], ta2: Task[A2]) =>
    //     (toa1, ta2).map { case (oa1: Option[A1], a2: A2) => oa1 getOrElse b2 }
    //   }
  }

  private def coerceToAnyTaskSeq(tasks: Seq[AnyInitTask]): Seq[Def.Initialize[Task[Any]]] =
    tasks.asInstanceOf[Seq[Def.Initialize[Task[Any]]]]

  type AnyInitTask = Initialize[Task[_]]

  implicit def richTaskSeq[T](in: Seq[Initialize[Task[T]]]): RichTaskSeq[T] = new RichTaskSeq(in)
  final class RichTaskSeq[T](keys: Seq[Initialize[Task[T]]]) {
    def join: Initialize[Task[Seq[T]]] = tasks(_.join)
    def tasks: Initialize[Seq[Task[T]]] = Initialize.join(keys)
  }

  implicit def richAnyTaskSeq(in: Seq[AnyInitTask]): RichAnyTaskSeq = new RichAnyTaskSeq(in)
  final class RichAnyTaskSeq(keys: Seq[AnyInitTask]) {
    def dependOn: Initialize[Task[Unit]] =
      Initialize
        .joinAny[Task](coerceToAnyTaskSeq(keys))
        .apply(deps => nop.dependsOn(deps: _*))
  }

  sealed abstract class RichTaskables[K[+L[x]]](final val keys: K[Taskable])(using
      a: AList[K]
  ):

    type App[T] = Initialize[Task[T]]

    /** A higher-kinded function, where each parameter shares the same type constructor `M[_]`. */
    type Fun[M[_], Ret]

    /** Convert the higher-kinded function to a Function1.  For tuples that means call `.tupled`. */
    protected def convert[M[_], Ret](f: Fun[M, Ret]): K[M] => Ret

    private[this] val inputs: K[App] = a.transform(keys) {
      [A] => (fa: Taskable[A]) => fa.toTask
    }

    private[this] def onTasks[A1](f: K[Task] => Task[A1]): App[A1] =
      Def.app[SplitK[K, Task], Task[A1]](inputs)(f)(AList.asplit[K, Task](a))

    def flatMapN[T](f: Fun[Id, Task[T]]): App[T] = onTasks(_.flatMapN(convert(f)))
    def flatMapR[T](f: Fun[Result, Task[T]]): App[T] = onTasks(_.flatMapR(convert(f)))
    def mapN[T](f: Fun[Id, T]): App[T] = onTasks(_.mapR(convert(f) compose allM))
    def mapR[T](f: Fun[Result, T]): App[T] = onTasks(_.mapR(convert(f)))
    def flatFailure[T](f: Seq[Incomplete] => Task[T]): App[T] = onTasks(_ flatFailure f)
    def mapFailure[T](f: Seq[Incomplete] => T): App[T] = onTasks(_ mapFailure f)
  end RichTaskables

  // format: off

  type ST[X] = Taskable[X]
  final class RichTaskable1[A1](t1: ST[A1]) extends RichTaskables[[F[_]] =>> F[A1]](t1)(using AList.single[A1]):
    type Fun[M[_], Ret] = M[A1] => Ret
    def identityMap = mapN(identity)
    protected def convert[M[_], R](f: M[A1] => R) = f
  end RichTaskable1

  final class RichTaskable2[A, B](t2: (ST[A], ST[B])) extends RichTaskables[AList.Tuple2K[A, B]](t2)(using AList.tuple2[A, B]) {
    type Fun[M[_], Ret] = (M[A], M[B]) => Ret
    def identityMap = mapN(mkTuple2)
    protected def convert[M[_], R](f: (M[A], M[B]) => R) = f.tupled
  }

  final class RichTaskable3[A, B, C](t3: (ST[A], ST[B], ST[C])) extends RichTaskables[AList.Tuple3K[A, B, C]](t3)(using AList.tuple3[A, B, C]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C]) => Ret
    def identityMap = mapN(mkTuple3)
    protected def convert[M[_], R](f: Fun[M, R]) = f.tupled
  }

  final class RichTaskable4[A, B, C, D](t4: (ST[A], ST[B], ST[C], ST[D])) extends RichTaskables[AList.Tuple4K[A, B, C, D]](t4)(using AList.tuple4[A, B, C, D]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C], M[D]) => Ret
    def identityMap = mapN(mkTuple4)
    protected def convert[M[_], R](f: Fun[M, R]) = f.tupled
  }

  final class RichTaskable5[A, B, C, D, E](t5: (ST[A], ST[B], ST[C], ST[D], ST[E])) extends RichTaskables[AList.Tuple5K[A, B, C, D, E]](t5)(using AList.tuple5[A, B, C, D, E]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C], M[D], M[E]) => Ret
    def identityMap = mapN(mkTuple5)
    protected def convert[M[_], R](f: Fun[M, R]) = f.tupled
  }

  final class RichTaskable6[A, B, C, D, E, F](t6: (ST[A], ST[B], ST[C], ST[D], ST[E], ST[F])) extends RichTaskables[AList.Tuple6K[A, B, C, D, E, F]](t6)(using AList.tuple6[A, B, C, D, E, F]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C], M[D], M[E], M[F]) => Ret
    def identityMap = mapN(mkTuple6)
    protected def convert[M[_], R](z: Fun[M, R]) = z.tupled
  }

  final class RichTaskable7[A, B, C, D, E, F, G](t7: (ST[A], ST[B], ST[C], ST[D], ST[E], ST[F], ST[G])) extends RichTaskables[AList.Tuple7K[A, B, C, D, E, F, G]](t7)(using AList.tuple7[A, B, C, D, E, F, G]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G]) => Ret
    def identityMap = mapN(mkTuple7)
    protected def convert[M[_], R](z: Fun[M, R]) = z.tupled
  }

  final class RichTaskable8[A, B, C, D, E, F, G, H](t8: (ST[A], ST[B], ST[C], ST[D], ST[E], ST[F], ST[G], ST[H])) extends RichTaskables[AList.Tuple8K[A, B, C, D, E, F, G, H]](t8)(using AList.tuple8[A, B, C, D, E, F, G, H]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H]) => Ret
    def identityMap = mapN(mkTuple8)
    protected def convert[M[_], R](z: Fun[M, R]) = z.tupled
  }

  final class RichTaskable9[A, B, C, D, E, F, G, H, I](t9: (ST[A], ST[B], ST[C], ST[D], ST[E], ST[F], ST[G], ST[H], ST[I])) extends RichTaskables[AList.Tuple9K[A, B, C, D, E, F, G, H, I]](t9)(using AList.tuple9[A, B, C, D, E, F, G, H, I]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I]) => Ret
    def identityMap = mapN(mkTuple9)
    protected def convert[M[_], R](z: Fun[M, R]) = z.tupled
  }

  final class RichTaskable10[A, B, C, D, E, F, G, H, I, J](t10: ((ST[A], ST[B], ST[C], ST[D], ST[E], ST[F], ST[G], ST[H], ST[I], ST[J]))) extends RichTaskables[AList.Tuple10K[A, B, C, D, E, F, G, H, I, J]](t10)(using AList.tuple10[A, B, C, D, E, F, G, H, I, J]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J]) => Ret
    def identityMap = mapN(mkTuple10)
    protected def convert[M[_], R](z: Fun[M, R]) = z.tupled
  }

  final class RichTaskable11[A, B, C, D, E, F, G, H, I, J, K](t11: ((ST[A], ST[B], ST[C], ST[D], ST[E], ST[F], ST[G], ST[H], ST[I], ST[J], ST[K]))) extends RichTaskables[AList.Tuple11K[A, B, C, D, E, F, G, H, I, J, K]](t11)(using AList.tuple11[A, B, C, D, E, F, G, H, I, J, K]) {
    type Fun[M[_], Ret] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J], M[K]) => Ret
    def identityMap = mapN(mkTuple11)
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

  final class Apply2[A, B](t2: (Initialize[A], Initialize[B])):
    def apply[R](z: (A, B) => R) = Def.app[AList.Tuple2K[A, B], R](t2)(z.tupled)(AList.tuple2[A, B])
    def identity = apply(mkTuple2)
  end Apply2

  final class Apply3[A, B, C](t3: (Initialize[A], Initialize[B], Initialize[C])):
    def apply[T](z: (A, B, C) => T) = Def.app[AList.Tuple3K[A, B, C], T](t3)(z.tupled)(AList.tuple3[A, B, C])
    def identity = apply(mkTuple3)
  end Apply3

  final class Apply4[A, B, C, D](t4: (Initialize[A], Initialize[B], Initialize[C], Initialize[D])):
    def apply[T](z: (A, B, C, D) => T) = Def.app[AList.Tuple4K[A, B, C, D], T](t4)(z.tupled)(AList.tuple4[A, B, C, D])
    def identity = apply(mkTuple4)
  end Apply4

  final class Apply5[A, B, C, D, E](t5: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E])):
    def apply[T](z: (A, B, C, D, E) => T) = Def.app[AList.Tuple5K[A, B, C, D, E], T](t5)(z.tupled)(AList.tuple5[A, B, C, D, E])
    def identity = apply(mkTuple5)
  end Apply5

  final class Apply6[A, B, C, D, E, F](t6: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F])):
    def apply[T](z: (A, B, C, D, E, F) => T) = Def.app[AList.Tuple6K[A, B, C, D, E, F], T](t6)(z.tupled)(AList.tuple6[A, B, C, D, E, F])
    def identity = apply(mkTuple6)
  end Apply6

  final class Apply7[A, B, C, D, E, F, G](t7: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G])):
    def apply[T](z: (A, B, C, D, E, F, G) => T) = Def.app[AList.Tuple7K[A, B, C, D, E, F, G], T](t7)(z.tupled)(AList.tuple7[A, B, C, D, E, F, G])
    def identity = apply(mkTuple7)
  end Apply7

  final class Apply8[A, B, C, D, E, F, G, H](t8: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H])):
    def apply[T](z: (A, B, C, D, E, F, G, H) => T) = Def.app[AList.Tuple8K[A, B, C, D, E, F, G, H], T](t8)(z.tupled)(AList.tuple8[A, B, C, D, E, F, G, H])
    def identity = apply(mkTuple8)
  end Apply8

  final class Apply9[A, B, C, D, E, F, G, H, I](t9: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I])):
    def apply[T](z: (A, B, C, D, E, F, G, H, I) => T) = Def.app[AList.Tuple9K[A, B, C, D, E, F, G, H, I], T](t9)(z.tupled)(AList.tuple9[A, B, C, D, E, F, G, H, I])
    def identity = apply(mkTuple9)
  end Apply9

  final class Apply10[A, B, C, D, E, F, G, H, I, J](t10: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J])):
    def apply[T](z: (A, B, C, D, E, F, G, H, I, J) => T) = Def.app[AList.Tuple10K[A, B, C, D, E, F, G, H, I, J], T](t10)(z.tupled)(AList.tuple10[A, B, C, D, E, F, G, H, I, J])
    def identity = apply(mkTuple10)
  end Apply10

  final class Apply11[A, B, C, D, E, F, G, H, I, J, K](t11: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J], Initialize[K])):
    def apply[T](z: (A, B, C, D, E, F, G, H, I, J, K) => T) = Def.app[AList.Tuple11K[A, B, C, D, E, F, G, H, I, J, K], T](t11)(z.tupled)(AList.tuple11[A, B, C, D, E, F, G, H, I, J, K])
    def identity = apply(mkTuple11)
  end Apply11

  // format: on

  private[sbt] def extendScoped(s1: Scoped, ss: Seq[Scoped]): Seq[AttributeKey[_]] =
    s1.key +: ss.map(_.key)
end Scoped

/**
 * The sbt 0.10 style DSL was deprecated in 0.13.13, favouring the use of the '.value' macro.
 *
 * See https://www.scala-sbt.org/1.x/docs/Migrating-from-sbt-013x.html#Migrating+from+sbt+0.12+style for how to migrate.
 */
trait TupleSyntax:
  import Scoped._

  // format: off

  // this is the least painful arrangement I came up with
  type ST[T] = Taskable[T]
  implicit def taskableToTable1[A1](t1: ST[A1]): RichTaskable1[A1] = new RichTaskable1(t1)
  implicit def t2ToTable2[A, B](t2: (ST[A], ST[B])): RichTaskable2[A, B] = new RichTaskable2(t2)
  implicit def t3ToTable3[A, B, C](t3: (ST[A], ST[B], ST[C])): RichTaskable3[A, B, C] = new RichTaskable3(t3)
  implicit def t4ToTable4[A, B, C, D](t4: (ST[A], ST[B], ST[C], ST[D])): RichTaskable4[A, B, C, D] = new RichTaskable4(t4)
  implicit def t5ToTable5[A, B, C, D, E](t5: (ST[A], ST[B], ST[C], ST[D], ST[E])): RichTaskable5[A, B, C, D, E] = new RichTaskable5(t5)
  implicit def t6ToTable6[A, B, C, D, E, F](t6: (ST[A], ST[B], ST[C], ST[D], ST[E], ST[F])): RichTaskable6[A, B, C, D, E, F] = new RichTaskable6(t6)
  implicit def t7ToTable7[A, B, C, D, E, F, G](t7: (ST[A], ST[B], ST[C], ST[D], ST[E], ST[F], ST[G])): RichTaskable7[A, B, C, D, E, F, G] = new RichTaskable7(t7)
  implicit def t8ToTable8[A, B, C, D, E, F, G, H](t8: (ST[A], ST[B], ST[C], ST[D], ST[E], ST[F], ST[G], ST[H])): RichTaskable8[A, B, C, D, E, F, G, H] = new RichTaskable8(t8)
  implicit def t9ToTable9[A, B, C, D, E, F, G, H, I](t9: (ST[A], ST[B], ST[C], ST[D], ST[E], ST[F], ST[G], ST[H], ST[I])): RichTaskable9[A, B, C, D, E, F, G, H, I] = new RichTaskable9(t9)
  implicit def t10ToTable10[A, B, C, D, E, F, G, H, I, J](t10: (ST[A], ST[B], ST[C], ST[D], ST[E], ST[F], ST[G], ST[H], ST[I], ST[J])): RichTaskable10[A, B, C, D, E, F, G, H, I, J] = new RichTaskable10(t10)
  implicit def t11ToTable11[A, B, C, D, E, F, G, H, I, J, K](t11: (ST[A], ST[B], ST[C], ST[D], ST[E], ST[F], ST[G], ST[H], ST[I], ST[J], ST[K])): RichTaskable11[A, B, C, D, E, F, G, H, I, J, K] = new RichTaskable11(t11)

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
end TupleSyntax

object TupleSyntax extends TupleSyntax

import Scoped.{ coerceTag, extendScoped }

/** Constructs InputKeys, which are associated with input tasks to define a setting. */
object InputKey:

  def apply[A1: ClassTag](label: String): InputKey[A1] =
    apply[A1](label, "", KeyRanks.DefaultInputRank)

  def apply[A1: ClassTag](
      label: String,
      description: String,
  ): InputKey[A1] =
    apply(label, description, KeyRanks.DefaultInputRank)

  def apply[A1: ClassTag](
      label: String,
      description: String,
      rank: Int,
  ): InputKey[A1] =
    given mf: ClassTag[InputTask[A1]] =
      ManifestFactory.classType[InputTask[A1]](classOf[InputTask[A1]], coerceTag[A1])
    apply(AttributeKey[InputTask[A1]](label, description, rank))

  def apply[A1: ClassTag](
      label: String,
      description: String,
      extend1: Scoped,
      extendN: Scoped*
  ): InputKey[A1] = apply(label, description, KeyRanks.DefaultInputRank, extend1, extendN: _*)

  def apply[A1: ClassTag](
      label: String,
      description: String,
      rank: Int,
      extend1: Scoped,
      extendN: Scoped*
  ): InputKey[A1] =
    given mf: ClassTag[InputTask[A1]] =
      ManifestFactory.classType[InputTask[A1]](classOf[InputTask[A1]], coerceTag[A1])
    apply(AttributeKey[InputTask[A1]](label, description, extendScoped(extend1, extendN), rank))

  def apply[A1](akey: AttributeKey[InputTask[A1]]): InputKey[A1] =
    Scoped.scopedInput(Scope.ThisScope, akey)

end InputKey

/** Constructs TaskKeys, which are associated with tasks to define a setting. */
object TaskKey:
  def apply[A1: ClassTag](label: String): TaskKey[A1] =
    apply[A1](
      label = label,
      description = "",
      rank = Int.MaxValue,
    )

  def apply[A1: ClassTag](label: String, description: String): TaskKey[A1] =
    apply[A1](
      label = label,
      description = description,
      rank = Int.MaxValue,
    )

  def apply[A1: ClassTag](
      label: String,
      description: String,
      rank: Int,
  ): TaskKey[A1] =
    given mf: ClassTag[Task[A1]] =
      ManifestFactory.classType[Task[A1]](classOf[Task[A1]], coerceTag[A1])
    apply(AttributeKey[Task[A1]](label, description, rank))

  def apply[A1: ClassTag](
      label: String,
      description: String,
      extend1: Scoped,
      extendN: Scoped*
  ): TaskKey[A1] =
    given mf: ClassTag[Task[A1]] =
      ManifestFactory.classType[Task[A1]](classOf[Task[A1]], coerceTag[A1])
    apply(AttributeKey[Task[A1]](label, description, extendScoped(extend1, extendN)))

  def apply[A1: ClassTag](
      label: String,
      description: String,
      rank: Int,
      extend1: Scoped,
      extendN: Scoped*
  ): TaskKey[A1] =
    given mf: ClassTag[Task[A1]] =
      ManifestFactory.classType[Task[A1]](classOf[Task[A1]], coerceTag[A1])
    apply(AttributeKey[Task[A1]](label, description, extendScoped(extend1, extendN), rank))

  def apply[A1](akey: AttributeKey[Task[A1]]): TaskKey[A1] =
    Scoped.scopedTask(Scope.ThisScope, akey)

  def local[A1: ClassTag]: TaskKey[A1] =
    given mf: ClassTag[Task[A1]] =
      ManifestFactory.classType[Task[A1]](classOf[Task[A1]], coerceTag[A1])
    apply[A1](AttributeKey.local[Task[A1]])

end TaskKey

/** Constructs SettingKeys, which are associated with a value to define a basic setting. */
object SettingKey:
  def apply[A1: ClassTag: OptJsonWriter](
      label: String,
  ): SettingKey[A1] =
    apply[A1](
      label = label,
      description = "",
      rank = KeyRanks.DefaultSettingRank
    )

  def apply[A1: ClassTag: OptJsonWriter](
      label: String,
      description: String,
  ): SettingKey[A1] =
    apply[A1](
      label = label,
      description = description,
      rank = KeyRanks.DefaultSettingRank,
    )

  def apply[A1: ClassTag: OptJsonWriter](
      label: String,
      description: String,
      rank: Int
  ): SettingKey[A1] =
    apply(AttributeKey[A1](label, description, rank))

  def apply[A1: ClassTag: OptJsonWriter](
      label: String,
      description: String,
      extend1: Scoped,
      extendN: Scoped*
  ): SettingKey[A1] =
    apply(AttributeKey[A1](label, description, extendScoped(extend1, extendN)))

  def apply[A1: ClassTag: OptJsonWriter](
      label: String,
      description: String,
      rank: Int,
      extend1: Scoped,
      extendN: Scoped*
  ): SettingKey[A1] =
    apply(AttributeKey[A1](label, description, extendScoped(extend1, extendN), rank))

  def apply[A1](akey: AttributeKey[A1]): SettingKey[A1] =
    Scoped.scopedSetting(Scope.ThisScope, akey)

  def local[A1: ClassTag: OptJsonWriter]: SettingKey[A1] =
    apply[A1](AttributeKey.local[A1])

end SettingKey

class TupleWrap[Tup <: Tuple](value: Tuple.Map[Tup, Taskable]):
  type InitTask[A2] = Initialize[Task[A2]]
  lazy val alist = AList.tuple[Tup]
  lazy val initTasks =
    alist.transform[Taskable, InitTask](value)([a] => (t: Taskable[a]) => t.toTask)
  def mapN[A1](f: Tup => A1): Def.Initialize[Task[A1]] =
    import std.FullInstance.initializeTaskMonad
    alist.mapN[InitTask, A1](initTasks)(f.asInstanceOf[Tuple.Map[Tup, Id] => A1])
