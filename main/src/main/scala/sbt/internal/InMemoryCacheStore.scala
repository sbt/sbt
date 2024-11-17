/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
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
import sjsonnew.{ JsonReader, JsonWriter }

private[sbt] object InMemoryCacheStore {
  private class InMemoryCacheStore(maxSize: Long) extends AutoCloseable {
    private val weigher: Weigher[Path, (Any, Long, Int)] = { case (_, (_, _, size)) => size }
    private val files: Cache[Path, (Any, Long, Int)] = Caffeine
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

  private class CacheStoreImpl(path: Path, store: InMemoryCacheStore, cacheStore: CacheStore)
      extends CacheStore {
    override def delete(): Unit = cacheStore.delete()
    override def read[T]()(using reader: JsonReader[T]): T = {
      val lastModified = IO.getModifiedTimeOrZero(path.toFile)
      store.get[T](path) match {
        case Some((value, `lastModified`)) => value
        case _                             => cacheStore.read[T]()
      }
    }
    override def write[T](value: T)(using writer: JsonWriter[T]): Unit = {
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
  private def factory(
      store: InMemoryCacheStore,
      path: Path
  ): CacheStoreFactory = {
    val delegate = new DirectoryStoreFactory(path.toFile)
    new CacheStoreFactory {
      override def make(identifier: String): CacheStore =
        new CacheStoreImpl(path.resolve(identifier), store, delegate.make(identifier))
      override def sub(identifier: String): CacheStoreFactory =
        factory(store, path.resolve(identifier))
    }
  }
  private[sbt] trait CacheStoreFactoryFactory extends AutoCloseable {
    def apply(path: Path): CacheStoreFactory
  }
  private class CacheStoreFactoryFactoryImpl(size: Long) extends CacheStoreFactoryFactory {
    private val storeRef = new AtomicReference[InMemoryCacheStore]
    override def close(): Unit = Option(storeRef.get).foreach(_.close())
    def apply(
        path: Path,
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
      factory(store, path)
    }
  }
  private object DirectoryFactory extends CacheStoreFactoryFactory {
    override def apply(
        path: Path,
    ): CacheStoreFactory = new DirectoryStoreFactory(path.toFile)
    override def close(): Unit = {}
  }
  def factory(size: Long): CacheStoreFactoryFactory =
    if (size > 0) new CacheStoreFactoryFactoryImpl(size)
    else DirectoryFactory
}
