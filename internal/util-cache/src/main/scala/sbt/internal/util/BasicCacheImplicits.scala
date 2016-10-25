package sbt.internal.util

import java.net.{ URI, URL }

import sjsonnew.{ BasicJsonProtocol, JsonFormat }

trait BasicCacheImplicits { self: BasicJsonProtocol =>

  implicit def basicCache[I: JsonFormat: Equiv, O: JsonFormat]: Cache[I, O] =
    new BasicCache[I, O]()

  def defaultEquiv[T]: Equiv[T] =
    new Equiv[T] { def equiv(a: T, b: T) = a == b }

  def wrapEquiv[S, T](f: S => T)(implicit eqT: Equiv[T]): Equiv[S] =
    new Equiv[S] {
      def equiv(a: S, b: S) =
        eqT.equiv(f(a), f(b))
    }

  implicit def optEquiv[T](implicit t: Equiv[T]): Equiv[Option[T]] =
    new Equiv[Option[T]] {
      def equiv(a: Option[T], b: Option[T]) =
        (a, b) match {
          case (None, None)         => true
          case (Some(va), Some(vb)) => t.equiv(va, vb)
          case _                    => false
        }
    }
  implicit def urlEquiv(implicit uriEq: Equiv[URI]): Equiv[URL] = wrapEquiv[URL, URI](_.toURI)(uriEq)
  implicit def uriEquiv: Equiv[URI] = defaultEquiv
  implicit def stringSetEquiv: Equiv[Set[String]] = defaultEquiv
  implicit def stringMapEquiv: Equiv[Map[String, String]] = defaultEquiv

  implicit def arrEquiv[T](implicit t: Equiv[T]): Equiv[Array[T]] =
    wrapEquiv((x: Array[T]) => x: Seq[T])(seqEquiv[T](t))

  implicit def seqEquiv[T](implicit t: Equiv[T]): Equiv[Seq[T]] =
    new Equiv[Seq[T]] {
      def equiv(a: Seq[T], b: Seq[T]) =
        a.length == b.length &&
          ((a, b).zipped forall t.equiv)
    }

  def wrapIn[I, J](implicit f: I => J, g: J => I, jCache: SingletonCache[J]): SingletonCache[I] =
    new SingletonCache[I] {
      override def read(from: Input): I = g(jCache.read(from))
      override def write(to: Output, value: I) = jCache.write(to, f(value))
      override def equiv: Equiv[I] = wrapEquiv(f)(jCache.equiv)
    }

  def singleton[T](t: T): SingletonCache[T] =
    SingletonCache.basicSingletonCache(asSingleton(t), trueEquiv)

  def trueEquiv[T] = new Equiv[T] { def equiv(a: T, b: T) = true }
}
