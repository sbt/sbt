package sbt.util

import sjsonnew.{ BasicJsonProtocol, JsonFormat }

trait BasicCacheImplicits { self: BasicJsonProtocol =>

  implicit def basicCache[I: JsonFormat, O: JsonFormat]: Cache[I, O] =
    new BasicCache[I, O]()

  def wrapIn[I, J](implicit f: I => J, g: J => I, jCache: SingletonCache[J]): SingletonCache[I] =
    new SingletonCache[I] {
      override def read(from: Input): I = g(jCache.read(from))
      override def write(to: Output, value: I) = jCache.write(to, f(value))
    }

  def singleton[T](t: T): SingletonCache[T] =
    SingletonCache.basicSingletonCache(asSingleton(t))
}
