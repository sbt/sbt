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

/** Parses input and produces a task to run.  Constructed using the companion object. */
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

sealed trait Scoped { def scope: Scope; val key: AttributeKey[_] }

/** A common type for SettingKey and TaskKey so that both can be used as inputs to tasks.*/
sealed trait ScopedTaskable[T] extends Scoped

/** Identifies a setting.  It consists of three parts: the scope, the name, and the type of a value associated with this key.
* The scope is represented by a value of type Scope.
* The name and the type are represented by a value of type AttributeKey[T].
* Instances are constructed using the companion object. */
sealed trait SettingKey[T] extends ScopedTaskable[T] with KeyedInitialize[T] with Scoped.ScopingSetting[SettingKey[T]] with Scoped.DefinableSetting[T] with Scoped.ListSetting[T, Id]
{
	val key: AttributeKey[T]
	def scopedKey: ScopedKey[T] = ScopedKey(scope, key)
	def in(scope: Scope): SettingKey[T] = Scoped.scopedSetting(Scope.replaceThis(this.scope)(scope), this.key)

	protected[this] def make[S](other: Initialize[S])(f: (T, S) => T): Setting[T] = this <<= (this, other)(f)
}

/** Identifies a task.  It consists of three parts: the scope, the name, and the type of the value computed by a task associated with this key.
* The scope is represented by a value of type Scope.
* The name and the type are represented by a value of type AttributeKey[Task[T]].
* Instances are constructed using the companion object. */
sealed trait TaskKey[T] extends ScopedTaskable[T] with KeyedInitialize[Task[T]] with Scoped.ScopingSetting[TaskKey[T]] with Scoped.ListSetting[T, Task] with Scoped.DefinableTask[T]
{
	val key: AttributeKey[Task[T]]
	def scopedKey: ScopedKey[Task[T]] = ScopedKey(scope, key)
	def in(scope: Scope): TaskKey[T] = Scoped.scopedTask(Scope.replaceThis(this.scope)(scope), this.key)

	protected[this] def make[S](other: Initialize[Task[S]])(f: (T, S) => T): Setting[Task[T]] = this <<= (this, other) { (a,b) => (a,b) map f }
}

/** Identifies an input task.  An input task parses input and produces a task to run.
* It consists of three parts: the scope, the name, and the type of the value produced by an input task associated with this key.
* The scope is represented by a value of type Scope.
* The name and the type are represented by a value of type AttributeKey[InputTask[T]].
* Instances are constructed using the companion object. */
sealed trait InputKey[T] extends Scoped with KeyedInitialize[InputTask[T]] with Scoped.ScopingSetting[InputKey[T]] with Scoped.DefinableSetting[InputTask[T]]
{
	val key: AttributeKey[InputTask[T]]
	def scopedKey: ScopedKey[InputTask[T]] = ScopedKey(scope, key)
	def in(scope: Scope): InputKey[T] = Scoped.scopedInput(Scope.replaceThis(this.scope)(scope), this.key)
}

/** Methods and types related to constructing settings, including keys, scopes, and initializations. */
object Scoped
{
	implicit def taskScopedToKey[T](s: TaskKey[T]): ScopedKey[Task[T]] = ScopedKey(s.scope, s.key)
	implicit def inputScopedToKey[T](s: InputKey[T]): ScopedKey[InputTask[T]] = ScopedKey(s.scope, s.key)

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
	
	def scopedSetting[T](s: Scope, k: AttributeKey[T]): SettingKey[T]  =  new SettingKey[T] { val scope = s; val key = k}
	def scopedInput[T](s: Scope, k: AttributeKey[InputTask[T]]): InputKey[T]  =  new InputKey[T] { val scope = s; val key = k }
	def scopedTask[T](s: Scope, k: AttributeKey[Task[T]]): TaskKey[T]  =  new TaskKey[T] { val scope = s; val key = k }

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
		@deprecated("A call to 'identity' is no longer necessary and can be removed.", "0.11.0")
		final def identity: Initialize[S] = init
		def map[T](f: S => T): Initialize[Task[T]] = init(s => mktask(f(s)) )
		def flatMap[T](f: S => Task[T]): Initialize[Task[T]] = init(f)
	}
	sealed trait DefinableTask[S]
	{ self: TaskKey[S] =>

		private[sbt] def :==(value: S): Setting[Task[S]]  =  :=(value)
		private[sbt] def ::=(value: Task[S]): Setting[Task[S]]  =  Project.setting(scopedKey, Project.value( value ))
		def := (value: => S): Setting[Task[S]]  =  ::=(mktask(value))
		private[sbt] def :== (v: SettingKey[S]): Setting[Task[S]] = <<=( v(constant))
		def ~= (f: S => S): Setting[Task[S]]  =  Project.update(scopedKey)( _ map f )

		def <<= (app: Initialize[Task[S]]): Setting[Task[S]]  =  Project.setting(scopedKey, app)

		def task: SettingKey[Task[S]] = scopedSetting(scope, key)
		def get(settings: Settings[Scope]): Option[Task[S]] = settings.get(scope, key)

		@deprecated("A call to 'identity' is no longer necessary and can be removed.", "0.11.0")
		def identity: Initialize[Task[S]] = this

		def ? : Initialize[Task[Option[S]]] = Project.optional(scopedKey) { case None => mktask { None }; case Some(t) => t map some.fn }
		def ??[T >: S](or: => T): Initialize[Task[T]] = Project.optional(scopedKey)( _ getOrElse mktask(or) )
		def or[T >: S](i: Initialize[Task[T]]): Initialize[Task[T]] = (this.? zipWith i)( (x,y) => (x :^: y :^: KNil) map hf2( _ getOrElse _ ))
	}
	final class RichInitializeTask[S](i: Initialize[Task[S]]) extends RichInitTaskBase[S, Task]
	{
		protected def onTask[T](f: Task[S] => Task[T]): Initialize[Task[T]] = i apply f

		def dependsOn(tasks: AnyInitTask*): Initialize[Task[S]] = (i, Initialize.joinAny(tasks)) { (thisTask, deps) => thisTask.dependsOn(deps : _*) }

			import SessionVar.{persistAndSet, resolveContext, set, transform}

		def updateState(f: (State, S) => State): Initialize[Task[S]] = onTask(t => transform(t, f))
		def storeAs(key: TaskKey[S])(implicit f: sbinary.Format[S]): Initialize[Task[S]] = (Keys.resolvedScoped, i) { (scoped, task) =>
			transform(task, (state, value) => persistAndSet( resolveContext(key, scoped.scope, state), state, value)(f))
		}
		def keepAs(key: TaskKey[S]): Initialize[Task[S]] =
			(i, Keys.resolvedScoped)( (t,scoped) => transform(t, (state,value) => set(resolveContext(key, scoped.scope, state), state, value) ) )

		def triggeredBy(tasks: AnyInitTask*): Initialize[Task[S]] = nonLocal(tasks, Keys.triggeredBy)
		def runBefore(tasks: AnyInitTask*): Initialize[Task[S]] = nonLocal(tasks, Keys.runBefore)
		private[this] def nonLocal(tasks: Seq[AnyInitTask], key: AttributeKey[Seq[Task[_]]]): Initialize[Task[S]] =
			(Initialize.joinAny(tasks), i) { (ts, i) => i.copy(info = i.info.set(key, ts)) }
	}
	final class RichInitializeInputTask[S](i: Initialize[InputTask[S]]) extends RichInitTaskBase[S,InputTask]
	{
		protected def onTask[T](f: Task[S] => Task[T]): Initialize[InputTask[T]] = i(_ mapTask f)
		def dependsOn(tasks: AnyInitTask*): Initialize[InputTask[S]] = (i, Initialize.joinAny(tasks)) { (thisTask, deps) => thisTask.mapTask(_.dependsOn(deps : _*)) }
	}

	sealed abstract class RichInitTaskBase[S, R[_]]
	{
		protected def onTask[T](f: Task[S] => Task[T]): Initialize[R[T]]

		def flatMapR[T](f: Result[S] => Task[T]): Initialize[R[T]] = onTask(_ flatMapR f)
		def flatMap[T](f: S => Task[T]): Initialize[R[T]] = flatMapR(f compose successM)
		def map[T](f: S => T): Initialize[R[T]] = mapR(f compose successM)
		def mapR[T](f: Result[S] => T): Initialize[R[T]] = onTask(_ mapR f)
		def flatFailure[T](f: Incomplete => Task[T]): Initialize[R[T]] = flatMapR(f compose failM)
		def mapFailure[T](f: Incomplete => T): Initialize[R[T]] = mapR(f compose failM)
		def andFinally(fin: => Unit): Initialize[R[S]] = onTask(_ andFinally fin)
		def doFinally(t: Task[Unit]): Initialize[R[S]] = onTask(_ doFinally t)

		def || [T >: S](alt: Task[T]): Initialize[R[T]]  =  onTask(_ || alt)
		def && [T](alt: Task[T]): Initialize[R[T]]  =  onTask(_ && alt)

		def tag(tags: Tags.Tag*): Initialize[R[S]] = onTask(_.tag(tags: _*))
		def tagw(tags: (Tags.Tag, Int)*): Initialize[R[S]] = onTask(_.tagw(tags : _*))
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


	implicit def richFileSetting(s: SettingKey[File]): RichFileSetting = new RichFileSetting(s)
	implicit def richFilesSetting(s: SettingKey[Seq[File]]): RichFilesSetting = new RichFilesSetting(s)
	
	final class RichFileSetting(s: SettingKey[File]) extends RichFileBase
	{
		def /(c: String): Initialize[File] = s { _ / c }
		protected[this] def map0(f: PathFinder => PathFinder) = s(file => finder(f)(file :: Nil))
	}
	final class RichFilesSetting(s: SettingKey[Seq[File]]) extends RichFileBase
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
	* The general idea is to take a KList of ScopedTaskables, which are either SettingKeys or TaskKeys,
	*   and uniformly provide multi-flatMap/map for constructing a new Task.
	* For example: {{{ (SettingKeyA, TaskKeyB) flatMap { (a,b) => ... } }}}
	*/

	trait Reduced[HLs <: HList, HLt <: HList, HLv <: HList]
	{ o =>
		def settings: KList[Initialize, HLs]  // SS[A] :^: SS[Task[B]] :^: ...
		def tasks(hls: HLs): KList[Task, HLt]  // takes setting values from previous line to Task[B] :^: ...
		def expand(hls: HLs, hlt: Results[HLt]): Results[HLv] // takes Result[B] :+: ... to Value[A] :+: Result[B] :+: ...

		def prependTask[H](key: Initialize[Task[H]]) =
			new Reduced[Task[H] :+: HLs, H :+: HLt, H :+: HLv]
			{
				val settings = KCons(key, o.settings)
				def tasks(hls: Task[H] :+: HLs) = KCons(hls.head, o.tasks(hls.tail))
				def expand(hls: Task[H] :+: HLs, hlt: Results[H :+: HLt]) = KCons(hlt.head, o.expand(hls.tail, hlt.tail) )
			}

		def prependSetting[H](key: Initialize[H]) =
			new Reduced[H :+: HLs, HLt, H :+: HLv]
			{
				val settings = KCons(key, o.settings)
				def tasks(hls: H :+: HLs) = o.tasks(hls.tail)
				def expand(hls: H :+: HLs, hlt: Results[HLt]) = KCons(Value(hls.head), o.expand(hls.tail, hlt))
			}
		def prependTaskable[H](key: ScopedTaskable[H]): Reduced[_,_,H :+: HLv] =
			key match
			{
				case ss: SettingKey[H] => prependSetting(ss)
				case st: TaskKey[H] => prependTask(scopedSetting(st.scope, st.key))
			}

		def combine[D[_],S](c: Combine[D], f: Results[HLv] => D[S]): Initialize[Task[S]] =
			Project.app(settings)(hls => c(tasks(hls))(hlt => f(expand(hls, hlt))) )
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
	implicit def t10ToTable10[A,B,C,D,E,F,G,H,I,J](t10: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I], ScopedTaskable[J]) ): RichTaskable10[A,B,C,D,E,F,G,H,I,J] = new RichTaskable10(t10)
	implicit def t11ToTable11[A,B,C,D,E,F,G,H,I,J,K](t11: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I], ScopedTaskable[J], ScopedTaskable[K]) ): RichTaskable11[A,B,C,D,E,F,G,H,I,J,K] = new RichTaskable11(t11)
	implicit def t12ToTable12[A,B,C,D,E,F,G,H,I,J,K,L](t12: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I], ScopedTaskable[J], ScopedTaskable[K], ScopedTaskable[L]) ): RichTaskable12[A,B,C,D,E,F,G,H,I,J,K,L] = new RichTaskable12(t12)
	implicit def t13ToTable13[A,B,C,D,E,F,G,H,I,J,K,L,N](t13: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I], ScopedTaskable[J], ScopedTaskable[K], ScopedTaskable[L], ScopedTaskable[N]) ): RichTaskable13[A,B,C,D,E,F,G,H,I,J,K,L,N] = new RichTaskable13(t13)
	implicit def t14ToTable14[A,B,C,D,E,F,G,H,I,J,K,L,N,O](t14: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I], ScopedTaskable[J], ScopedTaskable[K], ScopedTaskable[L], ScopedTaskable[N], ScopedTaskable[O]) ): RichTaskable14[A,B,C,D,E,F,G,H,I,J,K,L,N,O] = new RichTaskable14(t14)
	implicit def t15ToTable15[A,B,C,D,E,F,G,H,I,J,K,L,N,O,P](t15: (ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I], ScopedTaskable[J], ScopedTaskable[K], ScopedTaskable[L], ScopedTaskable[N], ScopedTaskable[O], ScopedTaskable[P]) ): RichTaskable15[A,B,C,D,E,F,G,H,I,J,K,L,N,O,P] = new RichTaskable15(t15)
	
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
	final class RichTaskable10[A,B,C,D,E,F,G,H,I,J](t10: ((ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I], ScopedTaskable[J]))) extends RichTaskables(k10(t10))
	{
		type Fun[M[_],Ret] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J]) => Ret
		def identityMap = map(mkTuple10)
		protected def convertH[R](z: Fun[Id,R]) = hf10(z)
		protected def convertK[M[_],R](z: Fun[M,R]) = { case a :^: b :^: c :^: d :^: e :^: f :^: g :^: h :^: i :^: j :^: KNil => z(a,b,c,d,e,f,g,h,i,j) }
	}

	final class RichTaskable11[A,B,C,D,E,F,G,H,I,J,K](t11: ((ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I], ScopedTaskable[J], ScopedTaskable[K]))) extends RichTaskables(k11(t11))
	{
		type Fun[M[_],Ret] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J], M[K]) => Ret
		def identityMap = map(mkTuple11)
		protected def convertH[R](z: Fun[Id,R]) = hf11(z)
		protected def convertK[M[_],R](z: Fun[M,R]) = { case a :^: b :^: c :^: d :^: e :^: f :^: g :^: h :^: i :^: j :^: k :^: KNil => z(a,b,c,d,e,f,g,h,i,j,k) }
	}

	final class RichTaskable12[A,B,C,D,E,F,G,H,I,J,K,L](t12: ((ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I], ScopedTaskable[J], ScopedTaskable[K], ScopedTaskable[L]))) extends RichTaskables(k12(t12))
	{
		type Fun[M[_],Ret] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J], M[K], M[L]) => Ret
		def identityMap = map(mkTuple12)
		protected def convertH[R](z: Fun[Id,R]) = hf12(z)
		protected def convertK[M[_],R](z: Fun[M,R]) = { case a :^: b :^: c :^: d :^: e :^: f :^: g :^: h :^: i :^: j :^: k :^: l :^: KNil => z(a,b,c,d,e,f,g,h,i,j,k,l) }
	}

	final class RichTaskable13[A,B,C,D,E,F,G,H,I,J,K,L,N](t13: ((ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I], ScopedTaskable[J], ScopedTaskable[K], ScopedTaskable[L], ScopedTaskable[N]))) extends RichTaskables(k13(t13))
	{
		type Fun[M[_],Ret] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J], M[K], M[L], M[N]) => Ret
		def identityMap = map(mkTuple13)
		protected def convertH[R](z: Fun[Id,R]) = hf13(z)
		protected def convertK[M[_],R](z: Fun[M,R]) = { case a :^: b :^: c :^: d :^: e :^: f :^: g :^: h :^: i :^: j :^: k :^: l :^: n :^: KNil => z(a,b,c,d,e,f,g,h,i,j,k,l,n) }
	}

	final class RichTaskable14[A,B,C,D,E,F,G,H,I,J,K,L,N,O](t14: ((ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I], ScopedTaskable[J], ScopedTaskable[K], ScopedTaskable[L], ScopedTaskable[N], ScopedTaskable[O]))) extends RichTaskables(k14(t14))
	{
		type Fun[M[_],Ret] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J], M[K], M[L], M[N], M[O]) => Ret
		def identityMap = map(mkTuple14)
		protected def convertH[R](z: Fun[Id,R]) = hf14(z)
		protected def convertK[M[_],R](z: Fun[M,R]) = { case a :^: b :^: c :^: d :^: e :^: f :^: g :^: h :^: i :^: j :^: k :^: l :^: n :^: o :^: KNil => z(a,b,c,d,e,f,g,h,i,j,k,l,n,o) }
	}

	final class RichTaskable15[A,B,C,D,E,F,G,H,I,J,K,L,N,O,P](t15: ((ScopedTaskable[A], ScopedTaskable[B], ScopedTaskable[C], ScopedTaskable[D], ScopedTaskable[E], ScopedTaskable[F], ScopedTaskable[G], ScopedTaskable[H], ScopedTaskable[I], ScopedTaskable[J], ScopedTaskable[K], ScopedTaskable[L], ScopedTaskable[N], ScopedTaskable[O], ScopedTaskable[P]))) extends RichTaskables(k15(t15))
	{
		type Fun[M[_],Ret] = (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J], M[K], M[L], M[N], M[O], M[P]) => Ret
		def identityMap = map(mkTuple15)
		protected def convertH[R](z: Fun[Id,R]) = hf15(z)
		protected def convertK[M[_],R](z: Fun[M,R]) = { case a :^: b :^: c :^: d :^: e :^: f :^: g :^: h :^: i :^: j :^: k :^: l :^: n :^: o :^: p :^: KNil => z(a,b,c,d,e,f,g,h,i,j,k,l,n,o,p) }
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
    implicit def t10ToApp10[A,B,C,D,E,F,G,H,I,J](t10: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J]) ): Apply10[A,B,C,D,E,F,G,H,I,J] = new Apply10(t10)
    implicit def t11ToApp11[A,B,C,D,E,F,G,H,I,J,K](t11: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J], Initialize[K]) ): Apply11[A,B,C,D,E,F,G,H,I,J,K] = new Apply11(t11)
    implicit def t12ToApp12[A,B,C,D,E,F,G,H,I,J,K,L](t12: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J], Initialize[K], Initialize[L]) ): Apply12[A,B,C,D,E,F,G,H,I,J,K,L] = new Apply12(t12)
    implicit def t13ToApp13[A,B,C,D,E,F,G,H,I,J,K,L,N](t13: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J], Initialize[K], Initialize[L], Initialize[N]) ): Apply13[A,B,C,D,E,F,G,H,I,J,K,L,N] = new Apply13(t13)
    implicit def t14ToApp14[A,B,C,D,E,F,G,H,I,J,K,L,N,O](t14: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J], Initialize[K], Initialize[L], Initialize[N], Initialize[O]) ): Apply14[A,B,C,D,E,F,G,H,I,J,K,L,N,O] = new Apply14(t14)
    implicit def t15ToApp15[A,B,C,D,E,F,G,H,I,J,K,L,N,O,P](t15: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J], Initialize[K], Initialize[L], Initialize[N], Initialize[O], Initialize[P]) ): Apply15[A,B,C,D,E,F,G,H,I,J,K,L,N,O,P] = new Apply15(t15)

	def mkTuple2[A,B] = (a:A,b:B) => (a,b)
	def mkTuple3[A,B,C] = (a:A,b:B,c:C) => (a,b,c)
	def mkTuple4[A,B,C,D] = (a:A,b:B,c:C,d:D) => (a,b,c,d)
	def mkTuple5[A,B,C,D,E] = (a:A,b:B,c:C,d:D,e:E) => (a,b,c,d,e)
	def mkTuple6[A,B,C,D,E,F] = (a:A,b:B,c:C,d:D,e:E,f:F) => (a,b,c,d,e,f)
	def mkTuple7[A,B,C,D,E,F,G] = (a:A,b:B,c:C,d:D,e:E,f:F,g:G) => (a,b,c,d,e,f,g)
	def mkTuple8[A,B,C,D,E,F,G,H] = (a:A,b:B,c:C,d:D,e:E,f:F,g:G,h:H) => (a,b,c,d,e,f,g,h)
	def mkTuple9[A,B,C,D,E,F,G,H,I] = (a:A,b:B,c:C,d:D,e:E,f:F,g:G,h:H,i:I) => (a,b,c,d,e,f,g,h,i)
     def mkTuple10[A,B,C,D,E,F,G,H,I,J] = (a:A,b:B,c:C,d:D,e:E,f:F,g:G,h:H,i:I,j:J) => (a,b,c,d,e,f,g,h,i,j)
     def mkTuple11[A,B,C,D,E,F,G,H,I,J,K] = (a:A,b:B,c:C,d:D,e:E,f:F,g:G,h:H,i:I,j:J,k:K) => (a,b,c,d,e,f,g,h,i,j,k)
     def mkTuple12[A,B,C,D,E,F,G,H,I,J,K,L] = (a:A,b:B,c:C,d:D,e:E,f:F,g:G,h:H,i:I,j:J,k:K,l:L) => (a,b,c,d,e,f,g,h,i,j,k,l)
     def mkTuple13[A,B,C,D,E,F,G,H,I,J,K,L,N] = (a:A,b:B,c:C,d:D,e:E,f:F,g:G,h:H,i:I,j:J,k:K,l:L,n:N) => (a,b,c,d,e,f,g,h,i,j,k,l,n)
     def mkTuple14[A,B,C,D,E,F,G,H,I,J,K,L,N,O] = (a:A,b:B,c:C,d:D,e:E,f:F,g:G,h:H,i:I,j:J,k:K,l:L,n:N,o:O) => (a,b,c,d,e,f,g,h,i,j,k,l,n,o)
     def mkTuple15[A,B,C,D,E,F,G,H,I,J,K,L,N,O,P] = (a:A,b:B,c:C,d:D,e:E,f:F,g:G,h:H,i:I,j:J,k:K,l:L,n:N,o:O,p:P) => (a,b,c,d,e,f,g,h,i,j,k,l,n,o,p)

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
    final class Apply10[A,B,C,D,E,F,G,H,I,J](t10: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J])) {
        def apply[T](z: (A,B,C,D,E,F,G,H,I,J) => T) = Project.app( k10(t10) )( hf10(z) )
        def identity = apply(mkTuple10)
    }
    final class Apply11[A,B,C,D,E,F,G,H,I,J,K](t11: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J], Initialize[K])) {
        def apply[T](z: (A,B,C,D,E,F,G,H,I,J,K) => T) = Project.app( k11(t11) )( hf11(z) )
        def identity = apply(mkTuple11)
    }
    final class Apply12[A,B,C,D,E,F,G,H,I,J,K,L](t12: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J], Initialize[K], Initialize[L])) {
        def apply[T](z: (A,B,C,D,E,F,G,H,I,J,K,L) => T) = Project.app( k12(t12) )( hf12(z) )
        def identity = apply(mkTuple12)
    }
    final class Apply13[A,B,C,D,E,F,G,H,I,J,K,L,N](t13: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J], Initialize[K], Initialize[L], Initialize[N])) {
        def apply[T](z: (A,B,C,D,E,F,G,H,I,J,K,L,N) => T) = Project.app( k13(t13) )( hf13(z) )
        def identity = apply(mkTuple13)
    }
    final class Apply14[A,B,C,D,E,F,G,H,I,J,K,L,N,O](t14: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J], Initialize[K], Initialize[L], Initialize[N], Initialize[O])) {
        def apply[T](z: (A,B,C,D,E,F,G,H,I,J,K,L,N,O) => T) = Project.app( k14(t14) )( hf14(z) )
        def identity = apply(mkTuple14)
    }
    final class Apply15[A,B,C,D,E,F,G,H,I,J,K,L,N,O,P](t15: (Initialize[A], Initialize[B], Initialize[C], Initialize[D], Initialize[E], Initialize[F], Initialize[G], Initialize[H], Initialize[I], Initialize[J], Initialize[K], Initialize[L], Initialize[N], Initialize[O], Initialize[P])) {
        def apply[T](z: (A,B,C,D,E,F,G,H,I,J,K,L,N,O,P) => T) = Project.app( k15(t15) )( hf15(z) )
        def identity = apply(mkTuple15)
    }

	def hf2[A, B, T](z: (A, B) => T): A :+: B :+: HNil => T = { case a :+: b :+: HNil => z(a,b) }
	def hf3[A, B, C, T](z: (A,B,C) => T): A :+: B :+: C :+: HNil => T = { case a :+: b :+: c :+: HNil => z(a,b,c) }
	def hf4[A, B, C, D, T](z: (A,B,C,D) => T): A :+: B :+: C :+: D :+: HNil => T = { case a :+: b :+: c :+: d :+: HNil => z(a,b,c,d) }
	def hf5[A, B, C, D, E, T](z: (A,B,C,D,E) => T): A :+: B :+: C :+: D :+: E :+: HNil => T = { case a :+: b :+: c :+: d :+: e :+: HNil => z(a,b,c,d,e) }
	def hf6[A, B, C, D, E, F, T](z: (A,B,C,D,E,F) => T): A :+: B :+: C :+: D :+: E :+: F :+: HNil => T = { case a :+: b :+: c :+: d :+: e :+: f :+: HNil => z(a,b,c,d,e,f) }
	def hf7[A, B, C, D, E, F, G, T](z: (A,B,C,D,E,F,G) => T): A :+: B :+: C :+: D :+: E :+: F :+: G :+: HNil => T = { case a :+: b :+: c :+: d :+: e :+: f :+: g :+: HNil => z(a,b,c,d,e,f,g) }
	def hf8[A, B, C, D, E, F, G, H, T](z: (A,B,C,D,E,F,G,H) => T): A :+: B :+: C :+: D :+: E :+: F :+: G :+: H :+: HNil => T = { case a :+: b :+: c :+: d :+: e :+: f :+: g :+: h :+: HNil => z(a,b,c,d,e,f,g,h) }
	def hf9[A, B, C, D, E, F, G, H, I, T](z: (A,B,C,D,E,F,G,H,I) => T): A :+: B :+: C :+: D :+: E :+: F :+: G :+: H :+: I :+: HNil => T = { case a :+: b :+: c :+: d :+: e :+: f :+: g :+: h :+: i :+: HNil => z(a,b,c,d,e,f,g,h,i) }
    def hf10[A,B,C,D,E,F,G,H,I,J,T](z: (A,B,C,D,E,F,G,H,I,J) => T): A:+:B:+:C:+:D:+:E:+:F:+:G:+:H:+:I:+:J :+: HNil => T = { case a:+:b:+:c:+:d:+:e:+:f:+:g:+:h:+:i:+:j :+: HNil => z(a,b,c,d,e,f,g,h,i,j) }
    def hf11[A,B,C,D,E,F,G,H,I,J,K,T](z: (A,B,C,D,E,F,G,H,I,J,K) => T): A:+:B:+:C:+:D:+:E:+:F:+:G:+:H:+:I:+:J:+:K :+: HNil => T = { case a:+:b:+:c:+:d:+:e:+:f:+:g:+:h:+:i:+:j:+:k :+: HNil => z(a,b,c,d,e,f,g,h,i,j,k) }
    def hf12[A,B,C,D,E,F,G,H,I,J,K,L,T](z: (A,B,C,D,E,F,G,H,I,J,K,L) => T): A:+:B:+:C:+:D:+:E:+:F:+:G:+:H:+:I:+:J:+:K:+:L :+: HNil => T = { case a:+:b:+:c:+:d:+:e:+:f:+:g:+:h:+:i:+:j:+:k:+:l :+: HNil => z(a,b,c,d,e,f,g,h,i,j,k,l) }
    def hf13[A,B,C,D,E,F,G,H,I,J,K,L,N,T](z: (A,B,C,D,E,F,G,H,I,J,K,L,N) => T): A:+:B:+:C:+:D:+:E:+:F:+:G:+:H:+:I:+:J:+:K:+:L:+:N :+: HNil => T = { case a:+:b:+:c:+:d:+:e:+:f:+:g:+:h:+:i:+:j:+:k:+:l:+:n :+: HNil => z(a,b,c,d,e,f,g,h,i,j,k,l,n) }
    def hf14[A,B,C,D,E,F,G,H,I,J,K,L,N,O,T](z: (A,B,C,D,E,F,G,H,I,J,K,L,N,O) => T): A:+:B:+:C:+:D:+:E:+:F:+:G:+:H:+:I:+:J:+:K:+:L:+:N:+:O :+: HNil => T = { case a:+:b:+:c:+:d:+:e:+:f:+:g:+:h:+:i:+:j:+:k:+:l:+:n:+:o :+: HNil => z(a,b,c,d,e,f,g,h,i,j,k,l,n,o) }
    def hf15[A,B,C,D,E,F,G,H,I,J,K,L,N,O,P,T](z: (A,B,C,D,E,F,G,H,I,J,K,L,N,O,P) => T): A:+:B:+:C:+:D:+:E:+:F:+:G:+:H:+:I:+:J:+:K:+:L:+:N:+:O:+:P :+: HNil => T = { case a:+:b:+:c:+:d:+:e:+:f:+:g:+:h:+:i:+:j:+:k:+:l:+:n:+:o:+:p :+: HNil => z(a,b,c,d,e,f,g,h,i,j,k,l,n,o,p) }


	def k2[M[_], A, B](t2: (M[A], M[B]) ) = t2._1 :^: t2._2 :^: KNil
	def k3[M[_], A, B, C](t3: (M[A], M[B], M[C]) ) = t3._1 :^: t3._2 :^: t3._3 :^: KNil
	def k4[M[_], A, B, C, D](t4: (M[A], M[B], M[C], M[D])) = t4._1 :^: t4._2 :^: t4._3 :^: t4._4 :^: KNil
	def k5[M[_], A, B, C, D, E](t5: (M[A], M[B], M[C], M[D], M[E])) = t5._1 :^: t5._2 :^: t5._3 :^: t5._4 :^: t5._5 :^: KNil
	def k6[M[_], A, B, C, D, E, F](t6: (M[A], M[B], M[C], M[D], M[E], M[F])) = t6._1 :^: t6._2 :^: t6._3 :^: t6._4 :^: t6._5 :^: t6._6 :^: KNil
	def k7[M[_], A, B, C, D, E, F, G](t7: (M[A], M[B], M[C], M[D], M[E], M[F], M[G])) = t7._1 :^: t7._2 :^: t7._3 :^: t7._4 :^: t7._5 :^: t7._6 :^: t7._7 :^: KNil
	def k8[M[_], A, B, C, D, E, F, G, H](t8: (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H])) = t8._1 :^: t8._2 :^: t8._3 :^: t8._4 :^: t8._5 :^: t8._6 :^: t8._7 :^: t8._8 :^: KNil
	def k9[M[_], A, B, C, D, E, F, G, H, I](t9: (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I])) = t9._1 :^: t9._2 :^: t9._3 :^: t9._4 :^: t9._5 :^: t9._6 :^: t9._7 :^: t9._8 :^: t9._9 :^: KNil
    def k10[M[_], A,B,C,D,E,F,G,H,I,J](t10: (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J])) =t10._1 :^:t10._2 :^:t10._3 :^:t10._4 :^:t10._5 :^:t10._6 :^:t10._7 :^:t10._8 :^:t10._9 :^:t10._10 :^: KNil
    def k11[M[_], A,B,C,D,E,F,G,H,I,J,K](t11: (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J], M[K])) =t11._1 :^:t11._2 :^:t11._3 :^:t11._4 :^:t11._5 :^:t11._6 :^:t11._7 :^:t11._8 :^:t11._9 :^:t11._10 :^:t11._11 :^: KNil
    def k12[M[_], A,B,C,D,E,F,G,H,I,J,K,L](t12: (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J], M[K], M[L])) =t12._1 :^:t12._2 :^:t12._3 :^:t12._4 :^:t12._5 :^:t12._6 :^:t12._7 :^:t12._8 :^:t12._9 :^:t12._10 :^:t12._11 :^:t12._12 :^: KNil
    def k13[M[_], A,B,C,D,E,F,G,H,I,J,K,L,N](t13: (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J], M[K], M[L], M[N])) =t13._1 :^:t13._2 :^:t13._3 :^:t13._4 :^:t13._5 :^:t13._6 :^:t13._7 :^:t13._8 :^:t13._9 :^:t13._10 :^:t13._11 :^:t13._12 :^:t13._13 :^: KNil
    def k14[M[_], A,B,C,D,E,F,G,H,I,J,K,L,N,O](t14: (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J], M[K], M[L], M[N], M[O])) =t14._1 :^:t14._2 :^:t14._3 :^:t14._4 :^:t14._5 :^:t14._6 :^:t14._7 :^:t14._8 :^:t14._9 :^:t14._10 :^:t14._11 :^:t14._12 :^:t14._13 :^:t14._14 :^: KNil
    def k15[M[_], A,B,C,D,E,F,G,H,I,J,K,L,N,O,P](t15: (M[A], M[B], M[C], M[D], M[E], M[F], M[G], M[H], M[I], M[J], M[K], M[L], M[N], M[O], M[P])) =t15._1 :^:t15._2 :^:t15._3 :^:t15._4 :^:t15._5 :^:t15._6 :^:t15._7 :^:t15._8 :^:t15._9 :^:t15._10 :^:t15._11 :^:t15._12 :^:t15._13 :^:t15._14 :^:t15._15 :^: KNil

	private[sbt] def extendScoped(s1: Scoped, ss: Seq[Scoped]): Seq[AttributeKey[_]]  =  s1.key +: ss.map(_.key)
}

	import Scoped.extendScoped

/** Constructs InputKeys, which are associated with input tasks to define a setting.*/
object InputKey
{
	def apply[T: Manifest](label: String, description: String = "", rank: Int = KeyRanks.DefaultInputRank): InputKey[T] =
		apply( AttributeKey[InputTask[T]](label, description, rank) )

	def apply[T: Manifest](label: String, description: String, extend1: Scoped, extendN: Scoped*): InputKey[T] =
		apply(label, description, KeyRanks.DefaultInputRank, extend1, extendN : _*)

	def apply[T: Manifest](label: String, description: String, rank: Int, extend1: Scoped, extendN: Scoped*): InputKey[T] =
		apply( AttributeKey[InputTask[T]](label, description, extendScoped(extend1, extendN), rank) )

	def apply[T](akey: AttributeKey[InputTask[T]]): InputKey[T] =
		new InputKey[T] { val key = akey; def scope = Scope.ThisScope }
}

/** Constructs TaskKeys, which are associated with tasks to define a setting.*/
object TaskKey
{
	def apply[T: Manifest](label: String, description: String = "", rank: Int = KeyRanks.DefaultTaskRank): TaskKey[T] =
		apply( AttributeKey[Task[T]](label, description, rank) )

	def apply[T: Manifest](label: String, description: String, extend1: Scoped, extendN: Scoped*): TaskKey[T] =
		apply( AttributeKey[Task[T]](label, description, extendScoped(extend1, extendN)) )

	def apply[T: Manifest](label: String, description: String, rank: Int, extend1: Scoped, extendN: Scoped*): TaskKey[T] =
		apply( AttributeKey[Task[T]](label, description, extendScoped(extend1, extendN), rank) )

	def apply[T](akey: AttributeKey[Task[T]]): TaskKey[T] =
		new TaskKey[T] { val key = akey; def scope = Scope.ThisScope }

	def local[T: Manifest]: TaskKey[T] = apply[T](AttributeKey.local[Task[T]])
}

/** Constructs SettingKeys, which are associated with a value to define a basic setting.*/
object SettingKey
{
	def apply[T: Manifest](label: String, description: String = "", rank: Int = KeyRanks.DefaultSettingRank): SettingKey[T] =
		apply( AttributeKey[T](label, description, rank) )

	def apply[T: Manifest](label: String, description: String, extend1: Scoped, extendN: Scoped*): SettingKey[T] =
		apply( AttributeKey[T](label, description, extendScoped(extend1, extendN)) )

	def apply[T: Manifest](label: String, description: String, rank: Int, extend1: Scoped, extendN: Scoped*): SettingKey[T] =
		apply( AttributeKey[T](label, description, extendScoped(extend1, extendN), rank) )

	def apply[T](akey: AttributeKey[T]): SettingKey[T] =
		new SettingKey[T] { val key = akey; def scope = Scope.ThisScope }

	def local[T: Manifest]: SettingKey[T] = apply[T](AttributeKey.local[T])
}
