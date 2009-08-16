package xsbt

import sbinary.{CollectionTypes, Format, JavaFormats}
import java.io.File

trait Cache[I,O]
{
	def apply(file: File)(i: I): Either[O, O => Unit]
}
trait SBinaryFormats extends CollectionTypes with JavaFormats with NotNull
{
	//TODO: add basic types minus FileFormat
}
object Cache extends BasicCacheImplicits with SBinaryFormats with HListCacheImplicits
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
			case Right(store) => NewTask(m.map { out => store(out); out })
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