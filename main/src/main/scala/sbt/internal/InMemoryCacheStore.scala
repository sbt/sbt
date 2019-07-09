/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.io.IOException
import java.lang.Math.toIntExact
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{ Files, Path }
import java.util.concurrent.atomic.AtomicReference

import com.github.benmanes.caffeine.cache.{ Cache, Caffeine, Weigher }
import sbt.io.IO
import sbt.util.{ CacheStore, CacheStoreFactory, DirectoryStoreFactory }
import sjsonnew.{ IsoString, JsonReader, JsonWriter, SupportConverter }

private[sbt] object InMemoryCacheStore {
  private[this] class InMemoryCacheStore(maxSize: Long) extends AutoCloseable {
    private[this] val weigher: Weigher[Path, (Any, Long, Int)] = { case (_, (_, _, size)) => size }
    private[this] val files: Cache[Path, (Any, Long, Int)] = Caffeine
      .newBuilder()
      .maximumWeight(maxSize)
      .weigher(weigher)
      .build()
    def get[T](path: Path): Option[(T, Long)] = {
      files.getIfPresent(path) match {
        case null                                   => None
        case (value: T @unchecked, lastModified, _) => Some((value, lastModified))
      }
    }
    def put(path: Path, value: Any, lastModified: Long): Unit = {
      try {
        if (lastModified > 0) {
          val attributes = Files.readAttributes(path, classOf[BasicFileAttributes])
          files.put(path, (value, lastModified, toIntExact(attributes.size)))
        }
      } catch {
        case _: IOException | _: ArithmeticException => files.invalidate(path)
      }
    }
    def remove(path: Path): Unit = files.invalidate(path)

    override def close(): Unit = {
      files.invalidateAll()
      files.cleanUp()
    }
  }

  private[this] class CacheStoreImpl(path: Path, store: InMemoryCacheStore, cacheStore: CacheStore)
      extends CacheStore {
    override def delete(): Unit = cacheStore.delete()
    override def read[T]()(implicit reader: JsonReader[T]): T = {
      val lastModified = IO.getModifiedTimeOrZero(path.toFile)
      store.get[T](path) match {
        case Some((value: T, `lastModified`)) => value
        case _                                => cacheStore.read[T]()
      }
    }
    override def write[T](value: T)(implicit writer: JsonWriter[T]): Unit = {
      /*
       * This may be inefficient if multiple threads are concurrently modifying the file.
       * There is an assumption that there will be little to no concurrency at the file level
       * of this cache. If this assumption is invalidated, we may need to do something more
       * complicated.
       */
      val lastModified = IO.getModifiedTimeOrZero(path.toFile)
      store.get[T](path) match {
        case Some((v, `lastModified`)) if v == value => // nothing has changed
        case _ =>
          store.remove(path)
          cacheStore.write(value)
          val newLastModified = System.currentTimeMillis
          IO.setModifiedTimeOrFalse(path.toFile, newLastModified)
          store.put(path, value, newLastModified)
      }
    }
    override def close(): Unit = {
      store.remove(path)
      cacheStore.close()
    }
  }
  private[this] def factory[J: IsoString](
      store: InMemoryCacheStore,
      path: Path,
      converter: SupportConverter[J]
  ): CacheStoreFactory = {
    val delegate = new DirectoryStoreFactory(path.toFile, converter)
    new CacheStoreFactory {
      override def make(identifier: String): CacheStore =
        new CacheStoreImpl(path.resolve(identifier), store, delegate.make(identifier))
      override def sub(identifier: String): CacheStoreFactory =
        factory(store, path.resolve(identifier), converter)
    }
  }
  private[sbt] trait CacheStoreFactoryFactory extends AutoCloseable {
    def apply[J: IsoString](path: Path, supportConverter: SupportConverter[J]): CacheStoreFactory
  }
  private[this] class CacheStoreFactoryFactoryImpl(size: Long) extends CacheStoreFactoryFactory {
    private[this] val storeRef = new AtomicReference[InMemoryCacheStore]
    override def close(): Unit = Option(storeRef.get).foreach(_.close())
    def apply[J: IsoString](
        path: Path,
        supportConverter: SupportConverter[J]
    ): CacheStoreFactory = {
      val store = storeRef.get match {
        case null =>
          storeRef.synchronized {
            storeRef.get match {
              case null =>
                val s = new InMemoryCacheStore(size)
                storeRef.set(s)
                s
              case s => s
            }
          }
        case s => s
      }
      factory(store, path, supportConverter)
    }
  }
  private[this] object DirectoryFactory extends CacheStoreFactoryFactory {
    override def apply[J: IsoString](
        path: Path,
        supportConverter: SupportConverter[J]
    ): CacheStoreFactory = new DirectoryStoreFactory(path.toFile, supportConverter)
    override def close(): Unit = {}
  }
  def factory(size: Long): CacheStoreFactoryFactory =
    if (size > 0) new CacheStoreFactoryFactoryImpl(size)
    else DirectoryFactory
}
