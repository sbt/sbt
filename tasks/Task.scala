package xsbt

import Task.{mapTask,bindTask, ITask}
import scala.collection.{mutable,immutable}

sealed trait Result[O] extends NotNull
final case class Value[O](t: O) extends Result[O]
sealed abstract class Task[O] extends Identity with Result[O]
{
	type Input
	def dependencies: TreeHashSet[Task[_]] // IMPORTANT!!  immutable.HashSet is NOT suitable. It has issues with multi-threaded access
	def map[N](f: O => N): ITask[O,N]
	def bind[N](f: O => Result[N]): ITask[O,N]
	def dependsOn(addDependencies: Task[_]*): ITask[Input,O]
	def named(s: String): ITask[Input,O]
}
private final class M[I,O,R <: Result[O]](name: Option[String])
	(val dependencies: TreeHashSet[Task[_]])(val extract: Results => I)(val compute: I => R)  extends Task[O]
{
	type Input = I
	def this(dependencies: Task[_]*)(extract: Results => I)(compute: I => R) =
		this(None)(TreeHashSet(dependencies: _*))(extract)(compute)

	final def dependsOn(addDependencies: Task[_]*) = new M(name)(dependencies ++ addDependencies)(extract)(compute)
	final def map[N](f: O => N) = mapTask(this)(_(this))(f)
	final def bind[N](f: O => Result[N]) = bindTask(this)(_(this))(f)
	final def named(s: String) =
		name match
		{
			case Some(n) => error("Cannot rename task already named '" + n + "'.  (Tried to rename it to '" + s + "')")
			case None => new M(Some(s))(dependencies)(extract)(compute)
		}
	final override def toString = "Task " + name.getOrElse("<anon>")
}
abstract class Identity extends NotNull
{
	final override def equals(o: Any) = o match { case a: AnyRef => this eq a; case _ => false }
	final override def hashCode = System.identityHashCode(this)
}

private trait Results extends NotNull
{
	def apply[O](task: Task[O]): O
	def contains(task: Task[_]): Boolean
}


object Task
{
	val empty = Task(())

	type ITask[I,O] = Task[O] { type Input = I }
	import Function.tupled
	def apply[O](o: => O): ITask[Unit,O] =
		new M[Unit,O,Value[O]]()(r => ())( u => Value(o) )
	def bindTask[I,O](dependencies: Task[_]*)(extract: Results => I)(compute: I => Result[O]): ITask[I,O] =
		new M[I,O,Result[O]](dependencies : _*)(extract)(compute)
	def mapTask[I,O](dependencies: Task[_]*)(extract: Results => I)(compute: I => O): ITask[I,O] =
		new M[I,O,Value[O]](dependencies : _*)(extract)(in => Value(compute(in)))

	private[xsbt] def extract[I,O](t: ITask[I,O], results: Results): I = t match { case m: M[I,O,_] => m.extract(results) }
	private[xsbt] def compute[I,O](t: ITask[I,O], input: I): Result[O] = t match { case m: M[I,O,_] => m.compute(input) }

	implicit def iterableToForkBuilder[A](t: Iterable[A]): ForkBuilderIterable[A] = new ForkBuilderIterable(t)
	final class ForkBuilderIterable[A] private[Task](a: Iterable[A]) extends NotNull
	{
		def fork[X](f: A => X): Iterable[ITask[Unit,X]] = a.map(x => Task(f(x)))
		def reduce(f: (A,A) => A): Task[A] = fork(x => x) reduce(f)
	}

	implicit def iterableToBuilder[O](t: Iterable[Task[O]]): BuilderIterable[O] = new BuilderIterable(t)
	final class BuilderIterable[O] private[Task](a: Iterable[Task[O]]) extends NotNull
	{
		//def mapBind[X](f: O => Task[_,X]): Iterable[Task[O,XO]] = a.map(_.bind(f))
		def join: Task[Iterable[O]] = join(identity[O])
		def join[X](f: O => X): Task[Iterable[X]] = mapTask(a.toSeq: _*)( r => a.map(t => r(t)) )(_.map(f))
		//def bindJoin[X](f: O => Task[_,X]): Task[Iterable[X],Iterable[X]] = mapBind(f).join
		def reduce(f: (O,O) => O): Task[O] =
		{
			def reduce2(list: List[Task[O]], accumulate: List[Task[O]]): List[Task[O]] =
				list match
				{
					case Nil => accumulate
					case x :: Nil => x :: accumulate
					case xa :: xb :: tail => reduce2(tail, ( (xa, xb) map f ) :: accumulate )
				}
			def reduce1(list: List[Task[O]]): Task[O] =
				list match
				{
					case Nil => error("Empty list")
					case x :: Nil => x
					case _ => reduce1(reduce2(list, Nil))
				}
			reduce1(a.toList)
		}
	}

	import metascala.HLists.{HList,HNil,HCons}
	sealed trait TList
	{
		type Head
		type Tail <: TList
		type HListType <: HList
		def tasks: List[Task[_]]
		def get(results: Results): HListType
	}
	final class TNil extends TList
	{
		type Head = Nothing
		type Tail = TNil
		type HListType = HNil
		def ::[A](t: Task[A]) = TCons[A,HNil,TNil](t, this)
		def tasks = Nil
		def get(results: Results) = HNil
	}
	final case class TCons[H, HL <: HList, T <: TList { type HListType = HL}](head: Task[H], tail: T) extends TList
	{
		type Head = H
		type Tail = T
		type This = TCons[H,HL,T]
		type HListType = HCons[H,HL]
		def ::[A](t: Task[A]) = TCons[A,HListType,This](t, this)
		def tasks = head :: tail.tasks
		def get(results: Results) = HCons(results(head), tail.get(results))

		def map[X](f: HListType => X): ITask[HListType,X] = mapTask(tasks: _*)(get)(f)
		def bind[X](f: HListType => Result[X]): ITask[HListType,X] = bindTask(tasks: _*)(get)(f)
		def join: ITask[HListType,HListType] = map(identity[HListType])
	}
	val TNil = new TNil

	implicit def twoToBuilder[A,B](t: (Task[A], Task[B]) ): Builder2[A,B] =
		t match { case (a,b) => new Builder2(a,b) }
	final class Builder2[A,B] private[Task](a: Task[A], b: Task[B]) extends NotNull
	{
		def map[X](f: (A,B) => X): ITask[(A,B),X] = mapTask(a,b)(r => (r(a), r(b)))(tupled(f))
		def bind[X](f: (A,B) => Result[X]): ITask[(A,B),X] = bindTask(a,b)( r => (r(a), r(b)) )(tupled(f))
	}

	implicit def threeToBuilder[A,B,C](t: (Task[A], Task[B], Task[C])): Builder3[A,B,C] = t match { case (a,b,c) => new Builder3(a,b,c) }
	final class Builder3[A,B,C] private[Task](a: Task[A], b: Task[B], c: Task[C]) extends NotNull
	{
		def map[X](f: (A,B,C) => X): ITask[(A,B,C),X] = mapTask(a,b,c)( r =>  (r(a), r(b), r(c)) )(tupled(f))
		def bind[X](f: (A,B,C) => Result[X]): ITask[(A,B,C),X] = bindTask(a,b,c)( r => (r(a), r(b), r(c)) )(tupled(f))
	}

	implicit def fourToBuilder[A,B,C,D](t: (Task[A], Task[B], Task[C], Task[D])): Builder4[A,B,C,D] = t match { case (a,b,c,d) => new Builder4(a,b,c,d) }
	final class Builder4[A,B,C,D] private[Task](a: Task[A], b: Task[B], c: Task[C], d: Task[D]) extends NotNull
	{
		def map[X](f: (A,B,C,D) => X): ITask[(A,B,C,D),X] = mapTask(a,b,c,d)( r =>  (r(a), r(b), r(c), r(d)) )(tupled(f))
		def bind[X](f: (A,B,C,D) => Result[X]): ITask[(A,B,C,D),X] = bindTask(a,b,c,d)( r => (r(a), r(b), r(c),r(d)) )(tupled(f))
	}
}
