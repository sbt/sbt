package xsbt

import Task.{mapTask,bindTask}
import scala.collection.{mutable,immutable}

sealed trait Result[O] extends NotNull
final case class Value[O](t: O) extends Result[O]
sealed abstract class Task[O] extends Identity with Result[O]
{
	def dependencies: TreeHashSet[Task[_]] // IMPORTANT!!  immutable.HashSet is NOT suitable. It has issues with multi-threaded access
	def map[N](f: O => N): Task[N]
	def bind[N](f: O => Result[N]): Task[N]
	def dependsOn(addDependencies: Task[_]*): Task[O]
	def named(s: String): Task[O]
}
private final class M[O,R <: Result[O]](name: Option[String])(val dependencies: TreeHashSet[Task[_]])(val compute: Results => R)  extends Task[O]
{
	def this(dependencies: Task[_]*)(compute: Results => R) =
		this(None)(TreeHashSet(dependencies: _*))(compute)

	final def dependsOn(addDependencies: Task[_]*) = new M(name)(dependencies ++ addDependencies)(compute)
	final def map[N](f: O => N) = mapTask(this)(f compose get)
	final def bind[N](f: O => Result[N]) = bindTask(this)(f compose get)
	final def named(s: String) =
		name match
		{
			case Some(n) => error("Cannot rename task already named '" + n + "'.  (Tried to rename it to '" + s + "')")
			case None => new M(Some(s))(dependencies)(compute)
		}
	final override def toString = "Task " + name.getOrElse("<anon$" + hashCode.toHexString + ">")

	private def get: (Results => O) = _(this)
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

	import Function.tupled
	def apply[O](o: => O): Task[O] = mapTask()( _ => o )
	def mapTask[O](dependencies: Task[_]*)(compute: Results => O): Task[O] =
		bindTask(dependencies : _*)(in => Value(compute(in)))
	def bindTask[O](dependencies: Task[_]*)(compute: Results => Result[O]): Task[O] =
		new M[O,Result[O]](dependencies : _*)(compute)

	private[xsbt] def compute[O](t: Task[O], results: Results): Result[O] = t match { case m: M[O,_] => m.compute(results) }

	implicit def iterableToForkBuilder[A](t: Iterable[A]): ForkBuilderIterable[A] = new ForkBuilderIterable(t)
	final class ForkBuilderIterable[A] private[Task](a: Iterable[A]) extends NotNull
	{
		def fork[X](f: A => X): Iterable[Task[X]] = forkTasks(x => Task(f(x)) )
		def forkTasks[X](f: A => Task[X]): Iterable[Task[X]] = a.map(x => f(x))
		def reduce(f: (A,A) => A): Task[A] = fork(x => x) reduce(f)
	}

	implicit def iterableToBuilder[O](t: Iterable[Task[O]]): BuilderIterable[O] = new BuilderIterable(t)
	final class BuilderIterable[O] private[Task](a: Iterable[Task[O]]) extends NotNull
	{
		//def mapBind[X](f: O => Task[X]): Iterable[Task[XO]] = a.map(_.bind(f))
		def join: Task[Iterable[O]] = join(identity[O])
		def joinIgnore: Task[Unit] = join.map(_ => ())
		def join[X](f: O => X): Task[Iterable[X]] = mapTask(a.toSeq: _*)( r => a map (f compose r.apply[O]) )
		//def bindJoin[X](f: O => Task[X]): Task[Iterable[X]] = mapBind(f).join
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

	implicit def twoToBuilder[A,B](t: (Task[A], Task[B]) ): Builder2[A,B] =
		t match { case (a,b) => new Builder2(a,b) }
	final class Builder2[A,B] private[Task](a: Task[A], b: Task[B]) extends NotNull
	{
		private def extract = (r: Results) => (r(a), r(b))
		private def compute[T](f: (A,B) => T) = tupled(f) compose extract
		def map[X](f: (A,B) => X): Task[X] = mapTask(a,b)(compute(f))
		def bind[X](f: (A,B) => Result[X]): Task[X] = bindTask(a,b)(compute(f))
	}

	implicit def threeToBuilder[A,B,C](t: (Task[A], Task[B], Task[C])): Builder3[A,B,C] = t match { case (a,b,c) => new Builder3(a,b,c) }
	final class Builder3[A,B,C] private[Task](a: Task[A], b: Task[B], c: Task[C]) extends NotNull
	{
		private def extract = (r: Results) => (r(a), r(b), r(c))
		private def compute[T](f: (A,B,C) => T) = tupled(f) compose extract
		def map[X](f: (A,B,C) => X): Task[X] = mapTask(a,b,c)(compute(f))
		def bind[X](f: (A,B,C) => Result[X]): Task[X] = bindTask(a,b,c)(compute(f))
	}

	implicit def fourToBuilder[A,B,C,D](t: (Task[A], Task[B], Task[C], Task[D])): Builder4[A,B,C,D] = t match { case (a,b,c,d) => new Builder4(a,b,c,d) }
	final class Builder4[A,B,C,D] private[Task](a: Task[A], b: Task[B], c: Task[C], d: Task[D]) extends NotNull
	{
		private def extract = (r: Results) => (r(a), r(b), r(c), r(d))
		private def compute[T](f: (A,B,C,D) => T) = tupled(f) compose extract
		def map[X](f: (A,B,C,D) => X): Task[X] = mapTask(a,b,c,d)( compute(f) )
		def bind[X](f: (A,B,C,D) => Result[X]): Task[X] = bindTask(a,b,c,d)( compute(f) )
	}
}
