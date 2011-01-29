/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

/** An abstraction on top of Settings for build configuration and task definition. */

	import Types._
	import std.TaskExtra._
	import Task._
	import Project.{ScopedKey, Setting}
	import complete.Parser
	import java.io.File
	import java.net.URI

sealed trait InputTask[T] {
	def parser: Parser[Task[T]]
}
object InputTask {
	def apply[T](p: Parser[Task[T]]): InputTask[T] = new InputTask[T] { def parser = p }
	def apply[I,T](p: Parser[I], c: I => Task[T]): InputTask[T] = apply(p map c)
}

sealed trait Scoped { def scope: Scope }
sealed trait ScopedTaskable[T] extends Scoped
sealed trait ScopedSetting[T] extends ScopedTaskable[T] { def key: AttributeKey[T] }
sealed trait ScopedTask[T] extends ScopedTaskable[T] { def key: AttributeKey[Task[T]] }
sealed trait ScopedInput[T] extends Scoped { def key: AttributeKey[InputTask[T]] }

sealed trait Key[T] extends Scoped { final def scope: Scope = Scope(This,This,This,This) }
final class SettingKey[T] private(val key: AttributeKey[T]) extends Key[T] with ScopedSetting[T]
final class TaskKey[T] private(val key: AttributeKey[Task[T]]) extends Key[T] with ScopedTask[T]
final class InputKey[T] private(val key: AttributeKey[InputTask[T]]) extends Key[InputTask[T]] with ScopedInput[T]

object Scoped
{
	implicit def richSettingScoped[T](s: ScopedSetting[T]): RichSettingScoped[T] = new RichSettingScoped[T](s.scope, s.key)
	implicit def richTaskScoped[T](s: ScopedTask[T]): RichTaskScoped[T] = new RichTaskScoped[T](s.scope, s.key)
	implicit def richInputScoped[T](s: ScopedInput[T]): RichInputScoped[T] = new RichInputScoped[T](s.scope, s.key)

	implicit def taskScoping[T](s: TaskKey[T]): ScopingTask[T] = new ScopingTask[T](s.key)

	implicit def settingScoping[T](s: SettingKey[T]): ScopingSetting[T, ScopedSetting[T]] =
		new ScopingSetting(s.key, scope => scopedSetting(scope, s.key))

	implicit def inputScoping[T](s: InputKey[T]): ScopingSetting[InputTask[T], ScopedInput[T]] =
		new ScopingSetting(s.key, scope => scopedInput(scope, s.key))

	final class ScopingSetting[T, Result](val key: AttributeKey[T], app0: Scope => Result)
	{
		def apply(s: Scope): Result = app0(s)

		def apply(p: ProjectRef): Result  =  apply(Select(p), This, This)
		def apply(t: TaskKey[_]): Result  =  apply(This, This, Select(t))
		def apply(c: ConfigKey): Result  =  apply(This, Select(c), This)
		def apply(c: ConfigKey, t: TaskKey[_]): Result  =  apply(This, Select(c), Select(t))
		def apply(p: ProjectRef, c: ConfigKey): Result  =  apply(Select(p), Select(c), This)
		def apply(p: ProjectRef, t: TaskKey[_]): Result  =  apply(Select(p), This, Select(t))
		def apply(p: ProjectRef, c: ConfigKey, t: TaskKey[_]): Result  =  apply(Select(p), Select(c), Select(t))
		def apply(p: ScopeAxis[ProjectRef], c: ScopeAxis[ConfigKey], t: ScopeAxis[TaskKey[_]]): Result = apply( Scope(p, c, convert(t), This) )
		private def convert(tk: ScopeAxis[TaskKey[_]]): ScopeAxis[AttributeKey[_]] =
			tk match {
				case Select(t) => Select(t.key)
				case This => This
				case Global => Global
			}
	}
	
	private[this] def scopedSetting[T](s: Scope, k: AttributeKey[T]): ScopedSetting[T] = new ScopedSetting[T] { val scope = s; val key = k }
	private[this] def scopedInput[T](s: Scope, k: AttributeKey[InputTask[T]]): ScopedInput[T] = new ScopedInput[T] { val scope = s; val key = k }

	final class ScopingTask[T](taskKey: AttributeKey[Task[T]])
	{
		def apply(p: ProjectRef): RichTaskScoped[T] = apply(Select(p), This)
		def apply(c: ConfigKey): RichTaskScoped[T] = apply(This, Select(c))
		def apply(p: ProjectRef, c: ConfigKey): RichTaskScoped[T] = apply(Select(p), Select(c))
		def apply(p: ScopeAxis[ProjectRef], c: ScopeAxis[ConfigKey]): ScopedTask[T]  =  apply(Scope(p, c, This, This))
		def apply(s: Scope): ScopedTask[T]  =  scopedTask(s, taskKey)
	}
	private[this] def scopedTask[T](s: Scope, k: AttributeKey[Task[T]]): ScopedTask[T] = new ScopedTask[T] { val scope = s; val key = k }

	sealed abstract class RichBaseScoped[S]
	{
		def scope: Scope
		def key: AttributeKey[S]
		protected final val scoped = ScopedKey(scope, key)
		protected final val scopedList = scoped :^: KNil
		
		final def :==(value: S): Setting[S]  =  :=(value)
		final def := (value: => S): Setting[S]  =  Project.value(scoped)(value)
		final def :~ (f: S => S): Setting[S]  =  Project.update(scoped)(f)
		final def :- (app: Apply[S]): Setting[S]  =  app toSetting scoped

		final def apply[T](f: S => T): Apply[T] = Apply.mk(scopedList)(hl => f(hl.head))

		final def get(settings: Settings[Scope]): Option[S] = settings.get(scope, key)
	}
	final class RichInputScoped[T](val scope: Scope, val key: AttributeKey[InputTask[T]]) extends RichBaseScoped[InputTask[T]]
	final class RichSettingScoped[S](val scope: Scope, val key: AttributeKey[S]) extends RichBaseScoped[S]
	{
		def map[T](f: S => T): Apply[Task[T]] = flatMap(s => task(f(s)) )
		def flatMap[T](f: S => Task[T]): Apply[Task[T]] = Apply.mk(scopedList)(hl => f(hl.head))
	}
	final class RichTaskScoped[S](scope: Scope, key: AttributeKey[Task[S]])
	{
		type ScS = Setting[Task[S]]
		def :==(value: S): ScS  =  :=(value)
		def :==(value: Task[S]): ScS  =  Project.value(scoped)( value )
		def := (value: => S): ScS  =  :==(task(value))
		def :== (v: TaskKey[S]): ScS = Project.app(scoped, ScopedKey(scope, v.key) :^: KNil)(_.head)
		def :~ (f: S => S): ScS  =  Project.update(scoped)( _ map f )

		def :- (app: App[S]): ScS  =  app toSetting scoped

		def get(settings: Settings[Scope]): Option[Task[S]] = settings.get(scope, key)

		type App[T] = Apply[Task[T]]
		private[this] def scoped = ScopedKey(scope, key)
		private[this] def mk[T](onTask: Task[S] => Task[T]): App[T] = Apply.mk(scoped :^: KNil)(hl => onTask(hl.head))
		
		def flatMapR[T](f: Result[S] => Task[T]): App[T] = mk(_ flatMapR f)
		def flatMap[T](f: S => Task[T]): App[T] = flatMapR(f compose successM)
		def map[T](f: S => T): App[T] = mapR(f compose successM)
		def mapR[T](f: Result[S] => T): App[T] = mk(_ mapR f)
		def flatFailure[T](f: Incomplete => Task[T]): App[T] = flatMapR(f compose failM)
		def mapFailure[T](f: Incomplete => T): App[T] = mapR(f compose failM)
		def andFinally(fin: => Unit): App[S] = mk(_ andFinally fin)
		def doFinally(t: Task[Unit]): App[S] = mk(_ doFinally t)

		def || [T >: S](alt: Task[T]): App[T]  =  mk(_ || alt)
		def && [T](alt: Task[T]): App[T]  =  mk(_ && alt)

		def dependsOn(tasks: ScopedTask[_]*): App[S] =
		{
			val in = KCons(scopedTask(scope, key), KList.fromList(tasks))
			Apply.tasks(in) { kl =>
				val KCons(h,t) = KList.kcons(kl)
				h.dependsOn(t.toList :_*)
			}
		}
	}

	/*
	* Reduced and combine provide support for mixed Setting/Task flatMap/map.
	* The general idea is to take a KList of ScopedTaskables, which are either ScopedSettings or ScopedTasks,
	*   and uniformly provide multi-flatMap/map for constructing a new Task.
	* For example: {{{ (SettingKeyA, TaskKeyB) flatMap { (a,b) => ... } }}}
	*/

	trait Reduced[HLs <: HList, HLt <: HList, HLv <: HList]
	{ o =>
		def settings: KList[ScopedSetting, HLs]  // SS[A] :^: SS[Task[B]] :^: ...
		def tasks(hls: HLs): KList[Task, HLt]  // takes setting values from previous line to Task[B] :^: ...
		def expand(hls: HLs, hlt: Results[HLt]): Results[HLv] // takes Result[B] :+: ... to Value[A] :+: Result[B] :+: ...

		def prependTask[H](key: ScopedSetting[Task[H]]) =
			new Reduced[Task[H] :+: HLs, H :+: HLt, H :+: HLv]
			{
				val settings = KCons(key, o.settings)
				def tasks(hls: Task[H] :+: HLs) = KCons(hls.head, o.tasks(hls.tail))
				def expand(hls: Task[H] :+: HLs, hlt: Results[H :+: HLt]) = KCons(hlt.head, o.expand(hls.tail, hlt.tail) )
			}

		def prependSetting[H](key: ScopedSetting[H]) =
			new Reduced[H :+: HLs, HLt, H :+: HLv]
			{
				val settings = KCons(key, o.settings)
				def tasks(hls: H :+: HLs) = o.tasks(hls.tail)
				def expand(hls: H :+: HLs, hlt: Results[HLt]) = KCons(Value(hls.head), o.expand(hls.tail, hlt))
			}
		def prependTaskable[H](key: ScopedTaskable[H]): Reduced[_,_,H :+: HLv] =
			key match
			{
				case ss: ScopedSetting[H] => prependSetting(ss)
				case st: ScopedTask[H] => prependTask(scopedSetting(st.scope, st.key))
			}

		def combine[D[_],S](c: Combine[D], f: Results[HLv] => D[S]): Apply[Task[S]] =
			Apply(settings)(hls => c(tasks(hls))(hlt => f(expand(hls, hlt))) )
	}
	type RedHL[HL <: HList] = Reduced[_,_,HL]
	def reduced[HL <: HList](settings: KList[ScopedTaskable, HL]): Reduced[_,_,HL] =
		settings.foldr { new KFold[ScopedTaskable, RedHL] {
			def knil = emptyReduced
			def kcons[H,T<:HList](h: ScopedTaskable[H], acc: Reduced[_,_,T]): Reduced[_,_,H :+: T] =
				acc prependTaskable h
		}}
	def emptyReduced: Reduced[HNil,HNil,HNil] = new Reduced[HNil,HNil,HNil] {
		def settings = KNil
		def tasks(h: HNil) = KNil
		def expand(a: HNil, k: Results[HNil]) = KNil
	}
	trait Combine[D[_]] {
		def apply[HL <: HList,S](tasks: KList[Task, HL])(f: Results[HL] => D[S]): Task[S]
	}
	object Combine
	{
		val mapR: Combine[Id] = new Combine[Id] {
			def apply[HL <: HList,S](tasks: KList[Task, HL])(f: Results[HL] => S): Task[S] =
				tasks mapR f
		}
		val flatMapR: Combine[Task] = new Combine[Task] {
			def apply[HL <: HList,S](tasks: KList[Task, HL])(f: Results[HL] => Task[S]): Task[S] =
				tasks flatMapR f
		}
	}

	implicit def richTaskableKeys[HL <: HList](in: KList[ScopedTaskable, HL]): RichTaskableKeys[HL] = new RichTaskableKeys(in)
	final class RichTaskableKeys[In <: HList](keys: KList[ScopedTaskable, In])
	{
		type App[T] = Apply[Task[T]]
		private[this] val red = reduced(keys)

		def flatMap[T](f: In => Task[T]): App[T] = flatMapR(f compose allM)
		def flatMapR[T](f: Results[In] => Task[T]): App[T] = red.combine(Combine.flatMapR, f)
		def map[T](f: In => T): App[T] = mapR(f compose allM)
		def mapR[T](f: Results[In] => T): App[T] = red.combine[Id,T](Combine.mapR, f)
		def flatFailure[T](f: Seq[Incomplete] => Task[T]): App[T] = flatMapR(f compose anyFailM)
		def mapFailure[T](f: Seq[Incomplete] => T): App[T] = mapR(f compose anyFailM)
	}

	implicit def t2ToApp2[A,B](t2: (ScopedSetting[A], ScopedSetting[B]) ): Apply2[A,B] = new Apply2(t2)
	implicit def t3ToApp3[A,B,C](t3: (ScopedSetting[A], ScopedSetting[B], ScopedSetting[C]) ): Apply3[A,B,C] = new Apply3(t3)

	final class Apply[T] private(val toSetting: ScopedKey[T] => Setting[T])

	object Apply
	{
		def mk[H <: HList, T](in: KList[ScopedKey, H])(f: H => T): Apply[T] =
			new Apply[T](scoped => Project.app(scoped, in)(f) )
			
		def apply[HL <: HList, T](in: KList[ScopedSetting, HL])(f: HL => T): Apply[T] = mk(in transform ssToSK)(f)
		def tasks[HL <: HList, T](in: KList[ScopedTask, HL])(f: KList[Task, HL] => T): Apply[T] =
		{
			val kapp = new Project.KApp[HL, Task, T]
			new Apply[T](scoped => kapp(scoped, in transform stToSK)(f) )
		}

		private val ssToSK = new (ScopedSetting ~> ScopedKey) { def apply[T](sk: ScopedSetting[T]) = new ScopedKey(sk.scope, sk.key) }
		private val stToSK = new (ScopedTask ~> ScopedTaskKey) { def apply[T](st: ScopedTask[T]) = new ScopedKey(st.scope, st.key) }
		private type ScopedTaskKey[T] = ScopedKey[Task[T]]
	}	

	final class Apply2[A,B](t2: (ScopedSetting[A], ScopedSetting[B])) {
		def apply[T](f: (A,B) => T) =
			Apply(t2._1 :^: t2._2 :^: KNil){ case a :+: b :+: HNil => f(a,b) }
	}
	final class Apply3[A,B,C](t3: (ScopedSetting[A], ScopedSetting[B], ScopedSetting[C])) {
		def apply[T](f: (A,B,C) => T) =
			Apply(t3._1 :^: t3._2 :^: t3._3 :^: KNil){ case a :+: b :+: c :+: HNil => f(a,b,c) }
	}

	/*def unresolved(scope: Scope): Seq[String] = unresolvedProject(scope.project) ++ unresolvedThis(scope)
	def unresolvedProject(ps: ScopeAxis[ProjectRef]): Seq[String] = ps match {
		case Select(p) => ifEmpty(p.unit, "Unspecified build unit") ++ ifEmpty(p.id, "Unspecified project ID")
		case _ => Nil
	}
	def ifEmpty(p: Option[URI], msg: String): Seq[String] = if(p.isEmpty) msg :: Nil else Nil
	def unresolvedThis(scope: Scope): Seq[String] =
		unresolvedThis(scope.project, "project") ++
		unresolvedThis(scope.config, "configuration") ++
		unresolvedThis(scope.task, "task") ++
		unresolvedThis(scope.extra, "extra")

	def unresolvedThis(axis: ScopeAxis[_], label: String): Seq[String] =
		if(axis == This) ("Unresolved This for " + label + " axis.") :: Nil else Nil*/
}
object InputKey
{
	def apply[T](label: String): InputKey[T] =
		apply( AttributeKey[InputTask[T]](label) )

	def apply[T](akey: AttributeKey[InputTask[T]]): InputKey[T] =
		new InputKey[T](akey)
}
object TaskKey
{
	def apply[T](label: String): TaskKey[T] =
		apply( AttributeKey[Task[T]](label) )

	def apply[T](akey: AttributeKey[Task[T]]): TaskKey[T] =
		new TaskKey[T](akey)
}
object SettingKey
{
	def apply[T](label: String): SettingKey[T] =
		apply( AttributeKey[T](label) )

	def apply[T](akey: AttributeKey[T]): SettingKey[T] =
		new SettingKey[T](akey)
}