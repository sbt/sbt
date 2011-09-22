/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

/** An abstraction on top of Settings for build configuration and task definition. */

	import Types._
	import std.TaskExtra.{task => mktask, _}
	import Task._
	import Project.{Initialize, KeyedInitialize, ScopedKey, Setting, setting}
	import complete.Parser
	import java.io.File
	import java.net.URI
	import Path._

sealed trait InputTask[T] {
	def mapTask[S](f: Task[T] => Task[S]): InputTask[S]
}
private final class InputStatic[T](val parser: State => Parser[Task[T]]) extends InputTask[T] {
	def mapTask[S](f: Task[T] => Task[S]) = new InputStatic(s => parser(s) map f)
}
private sealed trait InputDynamic[T] extends InputTask[T]
{ outer =>
	type Result
	def parser: State => Parser[Result]
	def defined: ScopedKey[_]
	def task: Task[T]
	def mapTask[S](f: Task[T] => Task[S]) = new InputDynamic[S] {
		type Result = outer.Result
		def parser = outer.parser
		def task = f(outer.task)
		def defined = outer.defined
	}
}
object InputTask
{
	def static[T](p: Parser[Task[T]]): InputTask[T] = free(_ => p)
	def static[I,T](p: Parser[I])(c: I => Task[T]): InputTask[T] = static(p map c)

	def free[T](p: State => Parser[Task[T]]): InputTask[T] = new InputStatic[T](p)
	def free[I,T](p: State => Parser[I])(c: I => Task[T]): InputTask[T] = free(s => p(s) map c)

	def separate[I,T](p: State => Parser[I])(action: Initialize[I => Task[T]]): Initialize[InputTask[T]] =
		separate(Project value p)(action)
	def separate[I,T](p: Initialize[State => Parser[I]])(action: Initialize[I => Task[T]]): Initialize[InputTask[T]] =
		p.zipWith(action)((parser, act) => free(parser)(act))
		
	private[sbt] lazy val inputMap: Task[Map[AnyRef,Any]] = mktask { error("Internal sbt error: input map not substituted.") }

	// This interface allows the Parser to be constructed using other Settings, but not Tasks (which is desired).
	// The action can be constructed using Settings and Tasks and with the parse result injected into a Task.
	// This is the ugly part, requiring hooks in Load.finalTransforms and Aggregation.applyDynamicTasks
	//  to handle the dummy task for the parse result.
	// However, this results in a minimal interface to the full capabilities of an InputTask for users
	def apply[I,T](p: Initialize[State => Parser[I]])(action: TaskKey[I] => Initialize[Task[T]]): Initialize[InputTask[T]] =
	{
		val key: TaskKey[I] = Keys.parseResult.asInstanceOf[TaskKey[I]]
		(p zip Keys.resolvedScoped zipWith action(key)) { case ((parserF, scoped), act) =>
			new InputDynamic[T]
			{
				type Result = I
				def parser = parserF
				def task = act
				def defined = scoped
			}
		}
	}
	def apply[I,T](p: State => Parser[I])(action: TaskKey[I] => Initialize[Task[T]]): Initialize[InputTask[T]] =
		apply(Project.value(p))(action)
}

sealed trait Scoped { def scope: Scope; def key: AttributeKey[_] }
sealed trait ScopedTaskable[T] extends Scoped
sealed trait ScopedSetting[T] extends ScopedTaskable[T] with KeyedInitialize[T] with Scoped.ScopingSetting[ScopedSetting[T]] with Scoped.DefinableSetting[T] with Scoped.ListSetting[T, Id]
{
	def key: AttributeKey[T]
	def scopedKey: ScopedKey[T] = ScopedKey(scope, key)
	def in(scope: Scope): ScopedSetting[T] = Scoped.scopedSetting(Scope.replaceThis(this.scope)(scope), this.key)

	protected[this] def make[S](other: Initialize[S])(f: (T, S) => T): Setting[T] = this <<= (this, other)(f)
}
sealed trait ScopedTask[T] extends ScopedTaskable[T] with KeyedInitialize[Task[T]] with Scoped.ScopingSetting[ScopedTask[T]] with Scoped.ListSetting[T, Task] with Scoped.DefinableTask[T]
{
	def key: AttributeKey[Task[T]]
	def scopedKey: ScopedKey[Task[T]] = ScopedKey(scope, key)
	def in(scope: Scope): ScopedTask[T] = Scoped.scopedTask(Scope.replaceThis(this.scope)(scope), this.key)

	protected[this] def make[S](other: Initialize[Task[S]])(f: (T, S) => T): Setting[Task[T]] = this <<= (this, other) { (a,b) => (a,b) map f }
}
sealed trait ScopedInput[T] extends Scoped with KeyedInitialize[InputTask[T]] with Scoped.ScopingSetting[ScopedInput[T]] with Scoped.DefinableSetting[InputTask[T]]
{
	def key: AttributeKey[InputTask[T]]
	def scopedKey: ScopedKey[InputTask[T]] = ScopedKey(scope, key)
	def in(scope: Scope): ScopedInput[T] = Scoped.scopedInput(Scope.replaceThis(this.scope)(scope), this.key)
}

sealed trait Key[T] extends Scoped { final def scope: Scope = Scope.ThisScope }
final class SettingKey[T] private(val key: AttributeKey[T]) extends Key[T] with ScopedSetting[T]
final class TaskKey[T] private(val key: AttributeKey[Task[T]]) extends Key[T] with ScopedTask[T]
final class InputKey[T] private(val key: AttributeKey[InputTask[T]]) extends Key[InputTask[T]] with ScopedInput[T]

object Scoped
{
	implicit def taskScopedToKey[T](s: ScopedTask[T]): ScopedKey[Task[T]] = ScopedKey(s.scope, s.key)
	implicit def inputScopedToKey[T](s: ScopedInput[T]): ScopedKey[InputTask[T]] = ScopedKey(s.scope, s.key)

	sealed trait ScopingSetting[Result]
	{
		def in(s: Scope): Result

		def in(p: Reference): Result  =  in(Select(p), This, This)
		def in(t: Scoped): Result  =  in(This, This, Select(t.key))
		def in(c: ConfigKey): Result  =  in(This, Select(c), This)
		def in(c: ConfigKey, t: Scoped): Result  =  in(This, Select(c), Select(t.key))
		def in(p: Reference, c: ConfigKey): Result  =  in(Select(p), Select(c), This)
		def in(p: Reference, t: Scoped): Result  =  in(Select(p), This, Select(t.key))
		def in(p: Reference, c: ConfigKey, t: Scoped): Result  =  in(Select(p), Select(c), Select(t.key))
		def in(p: ScopeAxis[Reference], c: ScopeAxis[ConfigKey], t: ScopeAxis[AttributeKey[_]]): Result = in( Scope(p, c, t, This) )
	}
	
	def scopedSetting[T](s: Scope, k: AttributeKey[T]): ScopedSetting[T]  =  new ScopedSetting[T] { val scope = s; val key = k}
	def scopedInput[T](s: Scope, k: AttributeKey[InputTask[T]]): ScopedInput[T]  =  new ScopedInput[T] { val scope = s; val key = k }
	def scopedTask[T](s: Scope, k: AttributeKey[Task[T]]): ScopedTask[T]  =  new ScopedTask[T] { val scope = s; val key = k }

	sealed trait ListSetting[S, M[_]]
	{
		protected[this] def make[T](other: Initialize[M[T]])(f: (S, T) => S): Setting[M[S]]
		protected[this] def ~=(f: S => S): Setting[M[S]]

		def <+= [V](value: Initialize[M[V]])(implicit a: Append.Value[S, V]): Setting[M[S]]  =  make(value)(a.appendValue)
		def <++= [V](values: Initialize[M[V]])(implicit a: Append.Values[S, V]): Setting[M[S]]  =  make(values)(a.appendValues)
		def += [U](value: => U)(implicit a: Append.Value[S, U]): Setting[M[S]]  =  this ~= ( v => a.appendValue(v, value) )
		def ++=[U](values: => U)(implicit a: Append.Values[S, U]): Setting[M[S]]  =  this ~= ( v => a.appendValues(v, values) )
	}
	sealed trait DefinableSetting[S]
	{
		def scopedKey: ScopedKey[S]

		private[sbt] final def :==(value: S): Setting[S]  =  :=(value)
		final def := (value: => S): Setting[S]  =  setting(scopedKey, Project.value(value))
		final def ~= (f: S => S): Setting[S]  =  Project.update(scopedKey)(f)
		final def <<= (app: Initialize[S]): Setting[S]  =  setting(scopedKey, app)
		final def get(settings: Settings[Scope]): Option[S] = settings.get(scopedKey.scope, scopedKey.key)
		final def ? : Initialize[Option[S]] = Project.optional(scopedKey)(idFun)
		final def or[T >: S](i: Initialize[T]): Initialize[T] = (this.?, i)(_ getOrElse _ )
		final def ??[T >: S](or: => T): Initialize[T] = Project.optional(scopedKey)(_ getOrElse or )
	}
	final class RichInitialize[S](init: Initialize[S])
	{
		@deprecated("A call to 'identity' is no longer necessary and can be removed.")
		final def identity: Initialize[S] = init
		def map[T](f: S => T): Initialize[Task[T]] = init(s => mktask(f(s)) )
		def flatMap[T](f: S => Task[T]): Initialize[Task[T]] = init(f)
	}
	sealed trait DefinableTask[S]
	{ self: ScopedTask[S] =>

		private[sbt] def :==(value: S): Setting[Task[S]]  =  :=(value)
		private[sbt] def ::=(value: Task[S]): Setting[Task[S]]  =  Project.setting(scopedKey, Project.value( value ))
		def := (value: => S): Setting[Task[S]]  =  ::=(mktask(value))
		private[sbt] def :== (v: ScopedSetting[S]): Setting[Task[S]] = <<=( v(constant))
		def ~= (f: S => S): Setting[Task[S]]  =  Project.update(scopedKey)( _ map f )

		def <<= (app: Initialize[Task[S]]): Setting[Task[S]]  =  Project.setting(scopedKey, app)

		def task: ScopedSetting[Task[S]] = scopedSetting(scope, key)
		def get(settings: Settings[Scope]): Option[Task[S]] = settings.get(scope, key)

		@deprecated("A call to 'identity' is no longer necessary and can be removed.")
		def identity: Initialize[Task[S]] = this

		def ? : Initialize[Task[Option[S]]] = Project.optional(scopedKey) { case None => mktask { None }; case Some(t) => t map some.fn }
		def ??[T >: S](or: => T): Initialize[Task[T]] = Project.optional(scopedKey)( _ getOrElse mktask(or) )
		def or[T >: S](i: Initialize[Task[T]]): Initialize[Task[T]] = (this.? zipWith scopedKey)( (x,y) => (x :^: y :^: KNil) map hf2( _ getOrElse _ ))
	}
	final class RichInitializeTask[S](i: Initialize[Task[S]])
	{
		def flatMapR[T](f: Result[S] => Task[T]): Initialize[Task[T]] = i(_ flatMapR f)
		def flatMap[T](f: S => Task[T]): Initialize[Task[T]] = flatMapR(f compose successM)
		def map[T](f: S => T): Initialize[Task[T]] = mapR(f compose successM)
		def mapR[T](f: Result[S] => T): Initialize[Task[T]] = i(_ mapR f)
		def flatFailure[T](f: Incomplete => Task[T]): Initialize[Task[T]] = flatMapR(f compose failM)
		def mapFailure[T](f: Incomplete => T): Initialize[Task[T]] = mapR(f compose failM)
		def andFinally(fin: => Unit): Initialize[Task[S]] = i(_ andFinally fin)
		def doFinally(t: Task[Unit]): Initialize[Task[S]] = i(_ doFinally t)

		def || [T >: S](alt: Task[T]): Initialize[Task[T]]  =  i(_ || alt)
		def && [T](alt: Task[T]): Initialize[Task[T]]  =  i(_ && alt)

		def dependsOn(tasks: AnyInitTask*): Initialize[Task[S]] = (i, Initialize.joinAny(tasks)) { (thisTask, deps) => thisTask.dependsOn(deps : _*) }

			import SessionVar.{persistAndSet, resolveContext, set, transform}

		def updateState(f: (State, S) => State): Initialize[Task[S]] = i(t => transform(t, f))
		def storeAs(key: ScopedTask[S])(implicit f: sbinary.Format[S]): Initialize[Task[S]] = (Keys.resolvedScoped, i) { (scoped, task) =>
			transform(task, (state, value) => persistAndSet( resolveContext(key, scoped.scope, state), state, value)(f))
		}
		def keepAs(key: ScopedTask[S]): Initialize[Task[S]] =
			(i, Keys.resolvedScoped)( (t,scoped) => transform(t, (state,value) => set(resolveContext(key, scoped.scope, state), state, value) ) )

		def triggeredBy(tasks: AnyInitTask*): Initialize[Task[S]] = nonLocal(tasks, Keys.triggeredBy)
		def runBefore(tasks: AnyInitTask*): Initialize[Task[S]] = nonLocal(tasks, Keys.runBefore)
		private[this] def nonLocal(tasks: Seq[AnyInitTask], key: AttributeKey[Seq[Task[_]]]): Initialize[Task[S]] =
			(Initialize.joinAny(tasks), i) { (ts, i) => i.copy(info = i.info.set(key, ts)) }
	}
	type AnyInitTask = Initialize[Task[T]] forSome { type T }

	implicit def richTaskSeq[T](in: Seq[Initialize[Task[T]]]): RichTaskSeq[T] = new RichTaskSeq(in)
	final class RichTaskSeq[T](keys: Seq[Initialize[Task[T]]])
	{
		def join: Initialize[Task[Seq[T]]] = tasks(_.join)
		def tasks: Initialize[Seq[Task[T]]] = Initialize.join(keys)
	}
	implicit def richAnyTaskSeq(in: Seq[AnyInitTask]): RichAnyTaskSeq = new RichAnyTaskSeq(in)
	final class RichAnyTaskSeq(keys: Seq[AnyInitTask])
	{
		def dependOn: Initialize[Task[Unit]]  =  Initialize.joinAny(keys).apply(deps => nop.dependsOn(deps : _*) )
	}


	implicit def richFileSetting(s: ScopedSetting[File]): RichFileSetting = new RichFileSetting(s)
	implicit def richFilesSetting(s: ScopedSetting[Seq[File]]): RichFilesSetting = new RichFilesSetting(s)
	
	final class RichFileSetting(s: ScopedSetting[File]) extends RichFileBase
	{
		def /(c: String): Initialize[File] = s { _ / c }
		protected[this] def map0(f: PathFinder => PathFinder) = s(file => finder(f)(file :: Nil))
	}
	final class RichFilesSetting(s: ScopedSetting[Seq[File]]) extends RichFileBase
	{
		def /(s: String): Initialize[Seq[File]] = map0 { _ / s }
		protected[this] def map0(f: PathFinder => PathFinder) = s(finder(f))
	}
	sealed abstract class RichFileBase
	{
		def *(filter: FileFilter): Initialize[Seq[File]] = map0 { _ * filter }
		def **(filter: FileFilter): Initialize[Seq[File]] = map0 { _ ** filter }
		protected[this] def map0(f: PathFinder => PathFinder): Initialize[Seq[File]]
		protected[this] def finder(f: PathFinder => PathFinder): Seq[File] => Seq[File] =
			in => f(in).get
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

		def combine[D[_],S](c: Combine[D], f: Results[HLv] => D[S]): Initialize[Task[S]] =
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
	
	// this is the least painful arrangement I came up with
	implicit def t2ToTable2[A,B](t2: (ScopedTaskable[A], ScopedTaskable[B]) ): RichTaskable2[A,B] = new RichTaskable2(t2)
	implicit def t3ToTable3[A,B,C](t3: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C]) ): RichTaskable3[A,B,C] = new RichTaskable3(t3)
	implicit def t4ToTable4[A,B,C,D](t4: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D]) ): RichTaskable4[A,B,C,D] = new RichTaskable4(t4)
	implicit def t5ToTable5[A,B,C,D,E](t5: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E]) ): RichTaskable5[A,B,C,D,E] = new RichTaskable5(t5)
	implicit def t6ToTable6[A,B,C,D,E,F](t6: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F]) ): RichTaskable6[A,B,C,D,E,F] = new RichTaskable6(t6)
	implicit def t7ToTable7[A,B,C,D,E,F,G](t7: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G]) ): RichTaskable7[A,B,C,D,E,F,G] = new RichTaskable7(t7)
	implicit def t8ToTable8[A,B,C,D,E,F,G,H](t8: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H]) ): RichTaskable8[A,B,C,D,E,F,G,H] = new RichTaskable8(t8)
	implicit def t9ToTable9[A,B,C,D,E,F,G,H,I](t9: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I]) ): RichTaskable9[A,B,C,D,E,F,G,H,I] = new RichTaskable9(t9)
	
	sealed abstract class RichTaskables[In <: HList](keys: KList[ScopedTaskable, In])
	{
		type App[T] = Initialize[Task[T]]
		type Fun[M[_],Ret]
		protected def convertH[Ret](f: Fun[Id,Ret]): In => Ret
		protected def convertK[M[_],Ret](f: Fun[M,Ret]): KList[M,In] => Ret
		private[this] val red = reduced(keys)

		def flatMap[T](f: Fun[Id,Task[T]]): App[T] = red.combine(Combine.flatMapR, convertH(f) compose allM)
		def flatMapR[T](f: Fun[Result,Task[T]]): App[T] = red.combine(Combine.flatMapR, convertK(f))
		def map[T](f: Fun[Id, T]): App[T] = red.combine[Id,T](Combine.mapR, convertH(f) compose allM)
		def mapR[T](f: Fun[Result,T]): App[T] = red.combine[Id,T](Combine.mapR, convertK(f))
		def flatFailure[T](f: Seq[Incomplete] => Task[T]): App[T] = red.combine(Combine.flatMapR, f compose anyFailM)
		def mapFailure[T](f: Seq[Incomplete] => T): App[T] = red.combine[Id,T](Combine.mapR, f compose anyFailM)
	}
	final class RichTaskable2[A,B](t2: (ScopedTaskable[A], ScopedTaskable[B])) extends RichTaskables(k2(t2))
	{
		type Fun[M[_],Ret] = (M[A],M[B]) => Ret
		def identityMap = map(mkTuple2)
		protected def convertH[R](z: (A,B) => R) = hf2(z)
		protected def convertK[M[_],R](f: (M[A],M[B]) => R) = { case a :^: b :^: KNil => f(a,b) }
	}
	final class RichTaskable3[A,B,C](t3: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C])) extends RichTaskables(k3(t3))
	{
		type Fun[M[_],Ret] = (M[A],M[B],M[C]) => Ret
		def identityMap = map(mkTuple3)
		protected def convertH[R](z: Fun[Id,R]) = hf3(z)
		protected def convertK[M[_],R](f: Fun[M,R]) = { case a :^: b :^: c :^: KNil => f(a,b,c) }
	}
	final class RichTaskable4[A,B,C,D](t4: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D])) extends RichTaskables(k4(t4))
	{
		type Fun[M[_],Ret] = (M[A],M[B],M[C],M[D]) => Ret
		def identityMap = map(mkTuple4)
		protected def convertH[R](z: Fun[Id,R]) = hf4(z)
		protected def convertK[M[_],R](f: Fun[M,R]) = { case a :^: b :^: c :^: d :^: KNil => f(a,b,c,d) }
	}
	final class RichTaskable5[A,B,C,D,E](t5: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E])) extends RichTaskables(k5(t5))
	{
		type Fun[M[_],Ret] = (M[A],M[B],M[C],M[D],M[E]) => Ret
		def identityMap = map(mkTuple5)
		protected def convertH[R](z: Fun[Id,R]) = hf5(z)
		protected def convertK[M[_],R](f: Fun[M,R]) = { case a :^: b :^: c :^: d :^: e :^: KNil => f(a,b,c,d,e) }
	}
	final class RichTaskable6[A,B,C,D,E,F](t6: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F])) extends RichTaskables(k6(t6))
	{
		type Fun[M[_],Ret] = (M[A],M[B],M[C],M[D],M[E],M[F]) => Ret
		def identityMap = map(mkTuple6)
		protected def convertH[R](z: Fun[Id,R]) = hf6(z)
		protected def convertK[M[_],R](z: Fun[M,R]) = { case a :^: b :^: c :^: d :^: e :^: f :^: KNil => z(a,b,c,d,e,f) }
	}
	final class RichTaskable7[A,B,C,D,E,F,G](t7: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G])) extends RichTaskables(k7(t7))
	{
		type Fun[M[_],Ret] = (M[A],M[B],M[C],M[D],M[E],M[F],M[G]) => Ret
		def identityMap = map(mkTuple7)
		protected def convertH[R](z: Fun[Id,R]) = hf7(z)
		protected def convertK[M[_],R](z: Fun[M,R]) = { case a :^: b :^: c :^: d :^: e :^: f :^: g :^: KNil => z(a,b,c,d,e,f,g) }
	}
	final class RichTaskable8[A,B,C,D,E,F,G,H](t8: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H])) extends RichTaskables(k8(t8))
	{
		type Fun[M[_],Ret] = (M[A],M[B],M[C],M[D],M[E],M[F],M[G],M[H]) => Ret
		def identityMap = map(mkTuple8)
		protected def convertH[R](z: Fun[Id,R]) = hf8(z)
		protected def convertK[M[_],R](z: Fun[M,R]) = { case a :^: b :^: c :^: d :^: e :^: f :^: g :^: h :^: KNil => z(a,b,c,d,e,f,g,h) }
	}
	final class RichTaskable9[A,B,C,D,E,F,G,H,I](t9: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I])) extends RichTaskables(k9(t9))
	{
		type Fun[M[_],Ret] = (M[A],M[B],M[C],M[D],M[E],M[F],M[G],M[H],M[I]) => Ret
		def identityMap = map(mkTuple9)
		protected def convertH[R](z: Fun[Id,R]) = hf9(z)
		protected def convertK[M[_],R](z: Fun[M,R]) = { case a :^: b :^: c :^: d :^: e :^: f :^: g :^: h :^: i :^: KNil => z(a,b,c,d,e,f,g,h,i) }
	}

	// this doesn't actually work for mixed KLists because the compiler crashes trying to infer the bound when constructing the KList
	implicit def richTaskableKeys[HL <: HList](in: KList[ScopedTaskable, HL]): RichTaskableKeys[HL] = new RichTaskableKeys(in)
	final class RichTaskableKeys[In <: HList](keys: KList[ScopedTaskable, In])
	{
		type App[T] = Initialize[Task[T]]
		private[this] val red = reduced(keys)

		def identity: App[In] = map(idFun)
		def flatMap[T](f: In => Task[T]): App[T] = flatMapR(f compose allM)
		def flatMapR[T](f: Results[In] => Task[T]): App[T] = red.combine(Combine.flatMapR, f)
		def map[T](f: In => T): App[T] = mapR(f compose allM)
		def mapR[T](f: Results[In] => T): App[T] = red.combine[Id,T](Combine.mapR, f)
		def flatFailure[T](f: Seq[Incomplete] => Task[T]): App[T] = flatMapR(f compose anyFailM)
		def mapFailure[T](f: Seq[Incomplete] => T): App[T] = mapR(f compose anyFailM)
	}

	implicit def t2ToApp2[A,B](t2: (Initialize[A], Initialize[B]) ): Apply2[A,B] = new Apply2(t2)
	implicit def t3ToApp3[A,B,C](t3: (Initialize[A], Initialize[B], Initialize[C]) ): Apply3[A,B,C] = new Apply3(t3)
	implicit def t4ToApp4[A,B,C,D](t4: (Initialize[A], Initialize[B], Initialize[C], Initialize[D]) ): Apply4[A,B,C,D] = new Apply4(t4)
	implicit def t5ToApp5[A,B,C,D,E](t5: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E]) ): Apply5[A,B,C,D,E] = new Apply5(t5)
	implicit def t6ToApp6[A,B,C,D,E,F](t6: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F]) ): Apply6[A,B,C,D,E,F] = new Apply6(t6)
	implicit def t7ToApp7[A,B,C,D,E,F,G](t7: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G]) ): Apply7[A,B,C,D,E,F,G] = new Apply7(t7)
	implicit def t8ToApp8[A,B,C,D,E,F,G,H](t8: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H]) ): Apply8[A,B,C,D,E,F,G,H] = new Apply8(t8)
	implicit def t9ToApp9[A,B,C,D,E,F,G,H,I](t9: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I]) ): Apply9[A,B,C,D,E,F,G,H,I] = new Apply9(t9)


	object Apply
	{
		def apply[HL <: HList, T](in: KList[ScopedSetting, HL])(f: HL => T): Initialize[T] =
			Project.app(in transform ssToSK)(f)

		private val ssToSK = new (ScopedSetting ~> ScopedKey) { def apply[T](sk: ScopedSetting[T]) = new ScopedKey(sk.scope, sk.key) }
	}	


	def mkTuple2[A,B] = (a:A,b:B) => (a,b)
	def mkTuple3[A,B,C] = (a:A,b:B,c:C) => (a,b,c)
	def mkTuple4[A,B,C,D] = (a:A,b:B,c:C,d:D) => (a,b,c,d)
	def mkTuple5[A,B,C,D,E] = (a:A,b:B,c:C,d:D,e:E) => (a,b,c,d,e)
	def mkTuple6[A,B,C,D,E,F] = (a:A,b:B,c:C,d:D,e:E,f:F) => (a,b,c,d,e,f)
	def mkTuple7[A,B,C,D,E,F,G] = (a:A,b:B,c:C,d:D,e:E,f:F,g:G) => (a,b,c,d,e,f,g)
	def mkTuple8[A,B,C,D,E,F,G,H] = (a:A,b:B,c:C,d:D,e:E,f:F,g:G,h:H) => (a,b,c,d,e,f,g,h)
	def mkTuple9[A,B,C,D,E,F,G,H,I] = (a:A,b:B,c:C,d:D,e:E,f:F,g:G,h:H,i:I) => (a,b,c,d,e,f,g,h,i)

	final class Apply2[A,B](t2: (Initialize[A], Initialize[B])) {
		def apply[T](z: (A,B) => T) = Project.app( k2(t2) )( hf2(z) )
		def identity = apply(mkTuple2)
	}
	final class Apply3[A,B,C](t3: (Initialize[A], Initialize[B], Initialize[C])) {
		def apply[T](z: (A,B,C) => T) = Project.app( k3(t3) )( hf3(z) )
		def identity = apply(mkTuple3)
	}
	final class Apply4[A,B,C,D](t4: (Initialize[A], Initialize[B], Initialize[C], Initialize[D])) {
		def apply[T](z: (A,B,C,D) => T) = Project.app( k4(t4) )( hf4(z) )
		def identity = apply(mkTuple4)
	}
	final class Apply5[A,B,C,D,E](t5: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E])) {
		def apply[T](z: (A,B,C,D,E) => T) = Project.app( k5(t5) )( hf5(z) )
		def identity = apply(mkTuple5)
	}
	final class Apply6[A,B,C,D,E,F](t6: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F])) {
		def apply[T](z: (A,B,C,D,E,F) => T) = Project.app( k6(t6) )( hf6(z) )
		def identity = apply(mkTuple6)
	}
	final class Apply7[A,B,C,D,E,F,G](t7: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G])) {
		def apply[T](z: (A,B,C,D,E,F,G) => T) = Project.app( k7(t7) )( hf7(z) )
		def identity = apply(mkTuple7)
	}
	final class Apply8[A,B,C,D,E,F,G,H](t8: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H])) {
		def apply[T](z: (A,B,C,D,E,F,G,H) => T) = Project.app( k8(t8) )( hf8(z) )
		def identity = apply(mkTuple8)
	}
	final class Apply9[A,B,C,D,E,F,G,H,I](t9: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I])) {
		def apply[T](z: (A,B,C,D,E,F,G,H,I) => T) = Project.app( k9(t9) )( hf9(z) )
		def identity = apply(mkTuple9)
	}

	def hf2[A, B, T](z: (A, B) => T): A :+: B :+: HNil => T = { case a :+: b :+: HNil => z(a,b) }
	def hf3[A, B, C, T](z: (A,B,C) => T): A :+: B :+: C :+: HNil => T = { case a :+: b :+: c :+: HNil => z(a,b,c) }
	def hf4[A, B, C, D, T](z: (A,B,C,D) => T): A :+: B :+: C :+: D :+: HNil => T = { case a :+: b :+: c :+: d :+: HNil => z(a,b,c,d) }
	def hf5[A, B, C, D, E, T](z: (A,B,C,D,E) => T): A :+: B :+: C :+: D :+: E :+: HNil => T = { case a :+: b :+: c :+: d :+: e :+: HNil => z(a,b,c,d,e) }
	def hf6[A, B, C, D, E, F, T](z: (A,B,C,D,E,F) => T): A :+: B :+: C :+: D :+: E :+: F :+: HNil => T = { case a :+: b :+: c :+: d :+: e :+: f :+: HNil => z(a,b,c,d,e,f) }
	def hf7[A, B, C, D, E, F, G, T](z: (A,B,C,D,E,F,G) => T): A :+: B :+: C :+: D :+: E :+: F :+: G :+: HNil => T = { case a :+: b :+: c :+: d :+: e :+: f :+: g :+: HNil => z(a,b,c,d,e,f,g) }
	def hf8[A, B, C, D, E, F, G, H, T](z: (A,B,C,D,E,F,G,H) => T): A :+: B :+: C :+: D :+: E :+: F :+: G :+: H :+: HNil => T = { case a :+: b :+: c :+: d :+: e :+: f :+: g :+: h :+: HNil => z(a,b,c,d,e,f,g,h) }
	def hf9[A, B, C, D, E, F, G, H, I, T](z: (A,B,C,D,E,F,G,H,I) => T): A :+: B :+: C :+: D :+: E :+: F :+: G :+: H :+: I :+: HNil => T = { case a :+: b :+: c :+: d :+: e :+: f :+: g :+: h :+: i :+: HNil => z(a,b,c,d,e,f,g,h,i) }


	def k2[M[_], A, B](t2: (M[A], M[B]) ) = t2._1 :^: t2._2 :^: KNil
	def k3[M[_], A, B, C](t3: (M[A], M[B], M[C]) ) = t3._1 :^: t3._2 :^: t3._3 :^: KNil
	def k4[M[_], A, B, C, D](t4: (M[A], M[B], M[C], M[D])) = t4._1 :^: t4._2 :^: t4._3 :^: t4._4 :^: KNil
	def k5[M[_], A, B, C, D, E](t5: (M[A], M[B], M[C], M[D], M[E])) = t5._1 :^: t5._2 :^: t5._3 :^: t5._4 :^: t5._5 :^: KNil
	def k6[M[_], A, B, C, D, E, F](t6: (M[A], M[B], M[C], M[D], M[E], M[F])) = t6._1 :^: t6._2 :^: t6._3 :^: t6._4 :^: t6._5 :^: t6._6 :^: KNil
	def k7[M[_], A, B, C, D, E, F, G](t7: (M[A], M[B], M[C], M[D], M[E], M[F], M[G])) = t7._1 :^: t7._2 :^: t7._3 :^: t7._4 :^: t7._5 :^: t7._6 :^: t7._7 :^: KNil
	def k8[M[_], A, B, C, D, E, F, G, H](t8: (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H])) = t8._1 :^: t8._2 :^: t8._3 :^: t8._4 :^: t8._5 :^: t8._6 :^: t8._7 :^: t8._8 :^: KNil
	def k9[M[_], A, B, C, D, E, F, G, H, I](t9: (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I])) = t9._1 :^: t9._2 :^: t9._3 :^: t9._4 :^: t9._5 :^: t9._6 :^: t9._7 :^: t9._8 :^: t9._9 :^: KNil

	private[sbt] def extendScoped(s1: Scoped, ss: Seq[Scoped]): Seq[AttributeKey[_]]  =  s1.key +: ss.map(_.key)
}

	import Scoped.extendScoped

object InputKey
{
	def apply[T: Manifest](label: String, description: String = ""): InputKey[T] =
		apply( AttributeKey[InputTask[T]](label, description) )

	def apply[T: Manifest](label: String, description: String, extend1: Scoped, extendN: Scoped*): InputKey[T] =
		apply( AttributeKey[InputTask[T]](label, description, extendScoped(extend1, extendN)) )

	def apply[T](akey: AttributeKey[InputTask[T]]): InputKey[T] =
		new InputKey[T](akey)
}
object TaskKey
{
	def apply[T: Manifest](label: String, description: String = ""): TaskKey[T] =
		apply( AttributeKey[Task[T]](label, description) )

	def apply[T: Manifest](label: String, description: String, extend1: Scoped, extendN: Scoped*): TaskKey[T] =
		apply( AttributeKey[Task[T]](label, description, extendScoped(extend1, extendN)) )

	def apply[T](akey: AttributeKey[Task[T]]): TaskKey[T] =
		new TaskKey[T](akey)
}
object SettingKey
{
	def apply[T: Manifest](label: String, description: String = ""): SettingKey[T] =
		apply( AttributeKey[T](label, description) )

	def apply[T: Manifest](label: String, description: String, extend1: Scoped, extendN: Scoped*): SettingKey[T] =
		apply( AttributeKey[T](label, description, extendScoped(extend1, extendN)) )

	def apply[T](akey: AttributeKey[T]): SettingKey[T] =
		new SettingKey[T](akey)
}