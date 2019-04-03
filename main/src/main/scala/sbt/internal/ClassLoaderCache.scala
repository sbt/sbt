/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.io.File
import java.nio.file.Files

import sbt.internal.util.TypeFunctions.Id

import scala.annotation.tailrec

private[sbt] sealed trait ClassLoaderCache
    extends Repository[Id, (Seq[File], ClassLoader, Map[String, String], File), ClassLoader]

private[sbt] object ClassLoaderCache {
  private type Resources = Map[String, String]
  private sealed trait CachedClassLoader extends ClassLoader {
    def close(): Unit
  }
  private sealed trait StableClassLoader extends CachedClassLoader
  private sealed trait SnapshotClassLoader extends CachedClassLoader
  def apply(maxSize: Int): ClassLoaderCache = {
    new ClassLoaderCache {
      private final def mktmp(tmp: File): File =
        if (maxSize > 0) Files.createTempDirectory("sbt-jni").toFile else tmp
      private[this] val lruCache =
        LRUCache[(JarClassPath, ClassLoader), (JarClassPath, CachedClassLoader)](
          maxSize = maxSize,
          onExpire =
            (_: (JarClassPath, ClassLoader), v: (JarClassPath, CachedClassLoader)) => close(v._2)
        )
      override def get(info: (Seq[File], ClassLoader, Resources, File)): ClassLoader =
        synchronized {
          val (paths, parent, resources, tmp) = info
          val key @ (keyJCP, _) = (new JarClassPath(paths), parent)
          def addLoader(base: Option[StableClassLoader] = None): CachedClassLoader = {
            val baseLoader = base.getOrElse {
              if (keyJCP.regularJars.isEmpty) new ClassLoader(parent) with StableClassLoader {
                override def close(): Unit = parent match {
                  case s: StableClassLoader => s.close()
                  case _                    => ()
                }
                override def toString: String = parent.toString
              } else
                new LayeredClassLoader(keyJCP.regularJars, parent, resources, mktmp(tmp))
                with StableClassLoader
            }
            val loader: CachedClassLoader =
              if (keyJCP.snapshotJars.isEmpty) baseLoader
              else
                new LayeredClassLoader(keyJCP.snapshotJars, baseLoader, resources, mktmp(tmp))
                with SnapshotClassLoader
            lruCache.put(key, keyJCP -> loader)
            loader
          }
          lruCache.get(key) match {
            case Some((jcp, cl)) if keyJCP.strictEquals(jcp) => cl
            case Some((_, cl: SnapshotClassLoader)) =>
              cl.close()
              cl.getParent match {
                case p: StableClassLoader => addLoader(Some(p))
                case _                    => addLoader()
              }
            case None => addLoader()
          }
        }
      override def close(): Unit = synchronized(lruCache.close())
      override def toString: String = {
        import PrettyPrint.indent
        val cacheElements = lruCache.entries.map {
          case ((jcp, parent), (_, l)) =>
            s"(\n${indent(jcp, 4)},\n${indent(parent, 4)}\n) =>\n  $l"
        }
        s"ClassLoaderCache(\n  size = $maxSize,\n  elements =\n${indent(cacheElements, 4)}\n)"
      }

      // Close the ClassLoader and all of it's closeable parents.
      @tailrec
      private def close(loader: CachedClassLoader): Unit = {
        loader.close()
        loader.getParent match {
          case c: CachedClassLoader => close(c)
          case _                    => ()
        }
      }
    }
  }
}
