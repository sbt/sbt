package xsbt

import sbinary.{CollectionTypes, Format, JavaFormats, Operations}
import java.io.File

trait Cache[I,O]
{
	def apply(file: File)(i: I): Either[O, O => Unit]
}
trait SBinaryFormats extends CollectionTypes with JavaFormats with NotNull
{
	//TODO: add basic types from SBinary minus FileFormat
}
object Cache extends BasicCacheImplicits with SBinaryFormats with HListCacheImplicits with TupleCacheImplicits
{
	def cache[I,O](implicit c: Cache[I,O]): Cache[I,O] = c
	def outputCache[O](implicit c: OutputCache[O]): OutputCache[O] = c
	def inputCache[O](implicit c: InputCache[O]): InputCache[O] = c

	def wrapInputCache[I,DI](implicit convert: I => DI, base: InputCache[DI]): InputCache[I] =
		new WrappedInputCache(convert, base)
	def wrapOutputCache[O,DO](implicit convert: O => DO, reverse: DO => O, base: OutputCache[DO]): OutputCache[O] =
		new WrappedOutputCache[O,DO](convert, reverse, base)

	/* Note: Task[O] { type Input = I } is written out because ITask[I,O] did not work (type could not be inferred properly) with a task
	* with an HList input.*/
	def apply[I,O](task: Task[O] { type Input = I }, file: File)(implicit cache: Cache[I,O]): Task[O] { type Input = I } =
		task match { case m: M[I,O,_] =>
			new M[I,O,Result[O]](None)(m.dependencies)(m.extract)(computeWithCache(m, cache, file))
		}
	private def computeWithCache[I,O](m: M[I,O,_], cache: Cache[I,O], file: File)(in: I): Result[O] =
		cache(file)(in) match
		{
			case Left(value) => Value(value)
			case Right(store) => m.map { out => store(out); out }
		}
}
trait BasicCacheImplicits extends NotNull
{
	implicit def basicInputCache[I](implicit format: Format[I], equiv: Equiv[I]): InputCache[I] =
		new BasicInputCache(format, equiv)
	implicit def basicOutputCache[O](implicit format: Format[O]): OutputCache[O] =
		new BasicOutputCache(format)

	implicit def ioCache[I,O](implicit input: InputCache[I], output: OutputCache[O]): Cache[I,O] =
		new SeparatedCache(input, output)
	implicit def defaultEquiv[T]: Equiv[T] = new Equiv[T] { def equiv(a: T, b: T) = a == b }
}
trait HListCacheImplicits extends HLists
{
	implicit def hConsInputCache[H,T<:HList](implicit headCache: InputCache[H], tailCache: InputCache[T]): InputCache[HCons[H,T]] =
		new HConsInputCache(headCache, tailCache)
	implicit lazy val hNilInputCache: InputCache[HNil] = new HNilInputCache

	implicit def hConsOutputCache[H,T<:HList](implicit headCache: OutputCache[H], tailCache: OutputCache[T]): OutputCache[HCons[H,T]] =
		new HConsOutputCache(headCache, tailCache)
	implicit lazy val hNilOutputCache: OutputCache[HNil] = new HNilOutputCache
}
trait TupleCacheImplicits extends HLists
{
	import Cache._
	implicit def tuple2HList[A,B](t: (A,B)): A :: B :: HNil = t._1 :: t._2 :: HNil
	implicit def hListTuple2[A,B](t: A :: B :: HNil): (A,B) = t match { case a :: b :: HNil => (a,b) }

	implicit def tuple2InputCache[A,B](implicit aCache: InputCache[A], bCache: InputCache[B]): InputCache[(A,B)] =
		wrapInputCache[(A,B), A :: B :: HNil]
	implicit def tuple2OutputCache[A,B](implicit aCache: OutputCache[A], bCache: OutputCache[B]): OutputCache[(A,B)] =
		wrapOutputCache[(A,B), A :: B :: HNil]

	implicit def tuple3HList[A,B,C](t: (A,B,C)): A :: B :: C :: HNil = t._1 :: t._2 :: t._3 :: HNil
	implicit def hListTuple3[A,B,C](t: A :: B :: C :: HNil): (A,B,C) = t match { case a :: b :: c :: HNil => (a,b,c) }

	implicit def tuple3InputCache[A,B,C](implicit aCache: InputCache[A], bCache: InputCache[B], cCache: InputCache[C]): InputCache[(A,B,C)] =
		wrapInputCache[(A,B,C), A :: B :: C :: HNil]
	implicit def tuple3OutputCache[A,B,C](implicit aCache: OutputCache[A], bCache: OutputCache[B], cCache: OutputCache[C]): OutputCache[(A,B,C)] =
		wrapOutputCache[(A,B,C), A :: B :: C :: HNil]

	implicit def tuple4HList[A,B,C,D](t: (A,B,C,D)): A :: B :: C :: D :: HNil = t._1 :: t._2 :: t._3 :: t._4 :: HNil
	implicit def hListTuple4[A,B,C,D](t: A :: B :: C :: D :: HNil): (A,B,C,D) = t match { case a :: b :: c :: d:: HNil => (a,b,c,d) }

	implicit def tuple4InputCache[A,B,C,D](implicit aCache: InputCache[A], bCache: InputCache[B], cCache: InputCache[C], dCache: InputCache[D]): InputCache[(A,B,C,D)] =
		wrapInputCache[(A,B,C,D), A :: B :: C :: D :: HNil]
	implicit def tuple4OutputCache[A,B,C,D](implicit aCache: OutputCache[A], bCache: OutputCache[B], cCache: OutputCache[C], dCache: OutputCache[D]): OutputCache[(A,B,C,D)] =
		wrapOutputCache[(A,B,C,D), A :: B :: C :: D :: HNil]
}