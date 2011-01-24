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
	type S
	def parser: Parser[S]
	def apply(s: S): Task[T]
}
object InputTask {
	def apply[I,T](p: Parser[I], c: I => Task[T]): InputTask[T] { type S = I } =
		new InputTask[T] { type S = I; def parser = p; def apply(s: I) = c(s) }
}

sealed trait Scoped { def scope: Scope }
sealed trait ScopedSetting[T] extends Scoped { def key: AttributeKey[T] }
sealed trait ScopedTask[T] extends Scoped { def key: AttributeKey[Task[T]] }
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

	implicit def settingScoping[T](s: SettingKey[T]): ScopingSetting[T] = new ScopingSetting[T](s.key)
	implicit def taskScoping[T](s: TaskKey[T]): ScopingTask[T] = new ScopingTask[T](s.key)
	implicit def inputScoping[T](s: InputKey[T]): ScopingInput[T] = new ScopingInput[T](s.key)

	sealed abstract class AbstractScoping[T]
	{
		type Result
		def key: AttributeKey[T]
		def apply(s: Scope): Result

		final def apply(p: ProjectRef): Result  =  apply(Select(p), This, This)
		final def apply(t: TaskKey[_]): Result  =  apply(This, This, Select(t))
		final def apply(c: ConfigKey): Result  =  apply(This, Select(c), This)
		final def apply(c: ConfigKey, t: TaskKey[_]): Result  =  apply(This, Select(c), Select(t))
		final def apply(p: ProjectRef, c: ConfigKey): Result  =  apply(Select(p), Select(c), This)
		final def apply(p: ProjectRef, t: TaskKey[_]): Result  =  apply(Select(p), This, Select(t))
		final def apply(p: ProjectRef, c: ConfigKey, t: TaskKey[_]): Result  =  apply(Select(p), Select(c), Select(t))
		final def apply(p: ScopeAxis[ProjectRef], c: ScopeAxis[ConfigKey], t: ScopeAxis[TaskKey[_]]): Result = apply( Scope(p, c, convert(t), This) )
		private def convert(tk: ScopeAxis[TaskKey[_]]): ScopeAxis[AttributeKey[_]] =
			tk match {
				case Select(t) => Select(t.key)
				case This => This
				case Global => Global
			}
	}
	final class ScopingSetting[T](val key: AttributeKey[T]) extends AbstractScoping[T]
	{
		type Result = ScopedSetting[T]
		def apply(s: Scope): Result  =  new ScopedSetting[T] { val scope = s; val key = ScopingSetting.this.key }
	}
	final class ScopingInput[T](val key: AttributeKey[InputTask[T]]) extends AbstractScoping[InputTask[T]]
	{
		type Result = ScopedInput[T]
		def apply(s: Scope): Result  =  new ScopedInput[T] { val scope = s; val key = ScopingInput.this.key }
	}
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
		final val scoped = ScopedKey(scope, key)
		final val scopedList = scoped :^: KNil
		
		final def :==(value: S): Setting[S]  =  :=(value)
		final def := (value: => S): Setting[S]  =  Project.value(scoped)(value)
		final def :~ (f: S => S): Setting[S]  =  Project.update(scoped)(f)
		final def :- (app: Apply[S]): Setting[S]  =  app toSetting scoped

		def apply[T](f: S => T): Apply[T] = Apply.mk(scopedList)(hl => f(hl.head))

		def get(settings: Settings[Scope]): Option[S] = settings.get(scope, key)
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
		def flatMap[T](f: S => Task[T]): App[T] = mk(_ flatMap f)
		def map[T](f: S => T): App[T] = mk(_ map f)
		def mapR[T](f: Result[S] => T): App[T] = mk(_ mapR f)
		def flatFailure[T](f: Incomplete => Task[T]): App[T] = mk(_ flatFailure f)
		def mapFailure[T](f: Incomplete => T): App[T] = mk(_ mapFailure f)
		def andFinally(fin: => Unit): App[S] = mk(_ andFinally fin)
		def doFinally(t: Task[Unit]): App[S] = mk(_ doFinally t)

		def || [T >: S](alt: Task[T]): App[T]  =  mk(_ || alt)
		def && [T](alt: Task[T]): App[T]  =  mk(_ && alt)

		def dependsOn(tasks: ScopedTask[_]*): App[S] =
		{
			val in = KCons(scopedTask(scope, key), KList.fromList(tasks))
			Apply.tasks(in) { case KCons(h,t) => h dependsOn(t.toList :_*) }
		}
	}

	implicit def richSettingKeys[HL <: HList](in: KList[ScopedSetting, HL]): RichSettingKeys[HL] = new RichSettingKeys(in)
	final class RichSettingKeys[HL <: HList](keys: KList[ScopedSetting, HL])
	{
		type App[T] = Apply[Task[T]]
		def map[T](f: HL => T): App[T] = Apply(keys)(settings => task(f(settings)))
		def flatMap[T](f: HL => Task[T]): App[T] = Apply(keys)(f)
	}
	final class RichTaskKeys[In <: HList](keys: KList[ScopedTask, In])
	{
		type App[T] = Apply[Task[T]]
		def flatMap[T](f: In => Task[T]): App[T] = mk(_ flatMap f)
		def flatMapR[T](f: Results[In] => Task[T]): App[T] = mk(_ flatMapR f)
		def map[T](f: In => T): App[T] = mk(_ map f)
		def mapR[T](f: Results[In] => T): App[T] = mk(_ mapR f)
		def flatFailure[T](f: Seq[Incomplete] => Task[T]): App[T] = mk(_ flatFailure f)
		def mapFailure[T](f: Seq[Incomplete] => T): App[T] = mk(_ mapFailure f)
		private[this] def mk[T](onTasks: KList[Task, In] => Task[T]): App[T] = Apply.tasks(keys)(onTasks)
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
	
//	private[this] val hcHead = new ( ({type l[t] = t :+: _})#l ~> Id ) { def apply[T](hc: t :+: _): t = hc.head }
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