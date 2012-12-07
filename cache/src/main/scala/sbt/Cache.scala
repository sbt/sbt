/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt

import sbinary.{CollectionTypes, DefaultProtocol, Format, Input, JavaFormats, Output => Out}
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, InputStream, OutputStream}
import java.net.{URI, URL}
import Types.:+:
import DefaultProtocol.{asProduct2, asSingleton, BooleanFormat, ByteFormat, IntFormat, wrap}
import scala.xml.NodeSeq

trait Cache[I,O]
{
	def apply(file: File)(i: I): Either[O, O => Unit]
}
trait SBinaryFormats extends CollectionTypes with JavaFormats
{
	implicit def urlFormat: Format[URL] = DefaultProtocol.UrlFormat
	implicit def uriFormat: Format[URI] = DefaultProtocol.UriFormat
}
object Cache extends CacheImplicits
{
	def cache[I,O](implicit c: Cache[I,O]): Cache[I,O] = c

	def cached[I,O](file: File)(f: I => O)(implicit cache: Cache[I,O]): I => O =
		in =>
			cache(file)(in) match
			{
				case Left(value) => value
				case Right(store) =>
					val out = f(in)
					store(out)
					out
			}

	def debug[I](label: String, c: InputCache[I]): InputCache[I] =
		new InputCache[I]
		{
			type Internal = c.Internal
			def convert(i: I) = c.convert(i)
			def read(from: Input) =
			{
				val v = c.read(from)
				println(label + ".read: " + v)
				v
			}
			def write(to: Out, v: Internal)
			{
				println(label + ".write: " + v)
				c.write(to, v)
			}
			def equiv: Equiv[Internal] = new Equiv[Internal] {
				def equiv(a: Internal, b: Internal)=
				{
					val equ = c.equiv.equiv(a,b)
					println(label + ".equiv(" + a + ", " + b +"): " + equ)
					equ
				}
			}
		}
}
trait CacheImplicits extends BasicCacheImplicits with SBinaryFormats with HListCacheImplicits with UnionImplicits
trait BasicCacheImplicits
{
	implicit def basicCache[I, O](implicit in: InputCache[I], outFormat: Format[O]): Cache[I,O] =
		new BasicCache()(in, outFormat)
	def basicInput[I](implicit eq: Equiv[I], fmt: Format[I]): InputCache[I] = InputCache.basicInputCache(fmt, eq)

	def defaultEquiv[T]: Equiv[T] = new Equiv[T] { def equiv(a: T, b: T) = a == b }
	
	implicit def optInputCache[T](implicit t: InputCache[T]): InputCache[Option[T]] =
		new InputCache[Option[T]]
		{
			type Internal = Option[t.Internal]
			def convert(v: Option[T]): Internal = v.map(x => t.convert(x))
			def read(from: Input) =
			{
				val isDefined = BooleanFormat.reads(from)
				if(isDefined) Some(t.read(from)) else None
			}
			def write(to: Out, j: Internal): Unit =
			{
				BooleanFormat.writes(to, j.isDefined)
				j foreach { x => t.write(to, x) }
			}
			def equiv = optEquiv(t.equiv)
		}
		
	def wrapEquiv[S,T](f: S => T)(implicit eqT: Equiv[T]): Equiv[S] =
		new Equiv[S] {
			def equiv(a: S, b: S) =
				eqT.equiv( f(a), f(b) )
		}

	implicit def optEquiv[T](implicit t: Equiv[T]): Equiv[Option[T]] =
		new Equiv[Option[T]] {
			def equiv(a: Option[T], b: Option[T]) =
				(a,b) match
				{
					case (None, None) => true
					case (Some(va), Some(vb)) => t.equiv(va, vb)
					case _ => false
				}
		}
	implicit def urlEquiv(implicit uriEq: Equiv[URI]): Equiv[URL] = wrapEquiv[URL, URI](_.toURI)(uriEq)
	implicit def uriEquiv: Equiv[URI] = defaultEquiv
	implicit def stringSetEquiv: Equiv[Set[String]] = defaultEquiv
	implicit def stringMapEquiv: Equiv[Map[String, String]] = defaultEquiv

	def streamFormat[T](write: (T, OutputStream) => Unit, f: InputStream => T): Format[T] =
	{
		val toBytes = (t: T) => { val bos = new ByteArrayOutputStream; write(t, bos); bos.toByteArray }
		val fromBytes = (bs: Array[Byte]) => f(new ByteArrayInputStream(bs))
		wrap(toBytes, fromBytes)(DefaultProtocol.ByteArrayFormat)
	}
	
	implicit def xmlInputCache(implicit strEq: InputCache[String]): InputCache[NodeSeq] = wrapIn[NodeSeq, String](_.toString, strEq)

	implicit def seqCache[T](implicit t: InputCache[T]): InputCache[Seq[T]] =
		new InputCache[Seq[T]]
		{
			type Internal = Seq[t.Internal]
			def convert(v: Seq[T]) = v.map(x => t.convert(x))
			def read(from: Input) =
			{
				val size = IntFormat.reads(from)
				def next(left: Int, acc: List[t.Internal]): Internal =
					if(left <= 0) acc.reverse else next(left - 1, t.read(from) :: acc)
				next(size, Nil)
			}
			def write(to: Out, vs: Internal)
			{
				val size = vs.length
				IntFormat.writes(to, size)
				for(v <- vs) t.write(to, v)
			}
			def equiv: Equiv[Internal] = seqEquiv(t.equiv)
		}

	implicit def arrEquiv[T](implicit t: Equiv[T]): Equiv[Array[T]] =
		wrapEquiv( (x: Array[T]) => x :Seq[T] )(seqEquiv[T](t))

	implicit def seqEquiv[T](implicit t: Equiv[T]): Equiv[Seq[T]] =
		new Equiv[Seq[T]]
		{
			def equiv(a: Seq[T], b: Seq[T]) =
				a.length == b.length &&
				((a,b).zipped forall t.equiv)
		}
	implicit def seqFormat[T](implicit t: Format[T]): Format[Seq[T]] =
		wrap[Seq[T], List[T]](_.toList, _.toSeq)(DefaultProtocol.listFormat)
	
	def wrapIn[I,J](implicit f: I => J, jCache: InputCache[J]): InputCache[I] =
		new InputCache[I]
		{
			type Internal = jCache.Internal
			def convert(i: I) = jCache.convert(f(i))
			def read(from: Input) = jCache.read(from)
			def write(to: Out, j: Internal) = jCache.write(to, j)
			def equiv = jCache.equiv
		}

	def singleton[T](t: T): InputCache[T] =
		basicInput(trueEquiv, asSingleton(t))

	def trueEquiv[T] = new Equiv[T] { def equiv(a: T, b: T) = true }
}

trait HListCacheImplicits
{
	implicit def hConsCache[H, T <: HList](implicit head: InputCache[H], tail: InputCache[T]): InputCache[H :+: T] =
		new InputCache[H :+: T]
		{
			type Internal = (head.Internal, tail.Internal)
			def convert(in: H :+: T) = (head.convert(in.head), tail.convert(in.tail))
			def read(from: Input) =
			{
				val h = head.read(from)
				val t = tail.read(from)
				(h, t)
			}
			def write(to: Out, j: Internal)
			{
				head.write(to, j._1)
				tail.write(to, j._2)
			}
			def equiv = new Equiv[Internal]
			{
				def equiv(a: Internal, b: Internal) =
					head.equiv.equiv(a._1, b._1) &&
					tail.equiv.equiv(a._2, b._2)
			}
		}
		
	implicit def hNilCache: InputCache[HNil] = Cache.singleton(HNil : HNil)

	implicit def hConsFormat[H, T <: HList](implicit head: Format[H], tail: Format[T]): Format[H :+: T] = new Format[H :+: T] {
		def reads(from: Input) =
		{
			val h = head.reads(from)
			val t = tail.reads(from)
			HCons(h, t)
		}
		def writes(to: Out, hc: H :+: T)
		{
			head.writes(to, hc.head)
			tail.writes(to, hc.tail)
		}
	}

	implicit def hNilFormat: Format[HNil] = asSingleton(HNil)
}
trait UnionImplicits
{
	def unionInputCache[UB, HL <: HList](implicit uc: UnionCache[HL, UB]): InputCache[UB] =
		new InputCache[UB]
		{
			type Internal = Found[_]
			def convert(in: UB) = uc.find(in)
			def read(in: Input) =
			{
				val index = ByteFormat.reads(in)
				val (cache, clazz) = uc.at(index)
				val value = cache.read(in)
				new Found[cache.Internal](cache, clazz, value, index)
			}
			def write(to: Out, i: Internal)
			{
				def write0[I](f: Found[I])
				{
					ByteFormat.writes(to, f.index.toByte)
					f.cache.write(to, f.value)
				}
				write0(i)
			}
			def equiv: Equiv[Internal] = new Equiv[Internal]
			{
				def equiv(a: Internal, b: Internal) =
				{
					if(a.clazz == b.clazz)
						force(a.cache.equiv, a.value, b.value)
					else
						false
				}
				def force[T <: UB, UB](e: Equiv[T], a: UB, b: UB) = e.equiv(a.asInstanceOf[T], b.asInstanceOf[T])
			}
		}

	implicit def unionCons[H <: UB, UB, T <: HList](implicit head: InputCache[H], mf: Manifest[H], t: UnionCache[T, UB]): UnionCache[H :+: T, UB] =
		new UnionCache[H :+: T, UB]
		{
			val size = 1 + t.size
			def c = mf.erasure
			def find(value: UB): Found[_] =
				if(c.isInstance(value)) new Found[head.Internal](head, c, head.convert(value.asInstanceOf[H]), size - 1) else t.find(value)
			def at(i: Int): (InputCache[_ <: UB], Class[_]) = if(size == i + 1) (head, c) else t.at(i)
		}

	implicit def unionNil[UB]: UnionCache[HNil, UB] = new UnionCache[HNil, UB] {
		def size = 0
		def find(value: UB) = error("No valid sum type for " + value)
		def at(i: Int) = error("Invalid union index " + i)
	}

	final class Found[I](val cache: InputCache[_] { type Internal = I }, val clazz: Class[_], val value: I, val index: Int)
	sealed trait UnionCache[HL <: HList, UB]
	{
		def size: Int
		def at(i: Int): (InputCache[_ <: UB], Class[_])
		def find(forValue: UB): Found[_]
	}
}