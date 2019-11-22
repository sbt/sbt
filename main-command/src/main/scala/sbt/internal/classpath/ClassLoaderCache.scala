/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.classpath

import java.io.File
import java.lang.management.ManagementFactory
import java.lang.ref.{ Reference, ReferenceQueue, SoftReference }
import java.net.URLClassLoader
import java.util.concurrent.atomic.AtomicInteger

import sbt.internal.inc.classpath.{
  AbstractClassLoaderCache,
  ClassLoaderCache => IncClassLoaderCache
}
import sbt.internal.inc.{ AnalyzingCompiler, ZincUtil }
import sbt.io.IO
import xsbti.ScalaProvider
import xsbti.compile.{ ClasspathOptions, ScalaInstance }

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

private object ClassLoaderCache {
  private def threadID = new AtomicInteger(0)
}
private[sbt] class ClassLoaderCache(
    override val commonParent: ClassLoader,
    private val miniProvider: Option[(File, ClassLoader)]
) extends AbstractClassLoaderCache {
  def this(commonParent: ClassLoader) = this(commonParent, None)
  def this(scalaProvider: ScalaProvider) =
    this(scalaProvider.launcher.topLoader, {
      scalaProvider.jars.find(_.getName == "scala-library.jar").flatMap { lib =>
        val clazz = scalaProvider.getClass
        try {
          val loader = clazz.getDeclaredMethod("libraryLoaderOnly").invoke(scalaProvider)
          Some(lib -> loader.asInstanceOf[ClassLoader])
        } catch { case NonFatal(_) => None }
      }
    })
  private val scalaProviderKey = miniProvider.map {
    case (f, cl) =>
      new Key((f -> IO.getModifiedTimeOrZero(f)) :: Nil, commonParent) {
        override def toClassLoader: ClassLoader = cl
      }
  }
  private class Key(val fileStamps: Seq[(File, Long)], val parent: ClassLoader) {
    def this(files: List[File]) =
      this(files.map(f => f -> IO.getModifiedTimeOrZero(f)), commonParent)
    lazy val files: Seq[File] = fileStamps.map(_._1)
    lazy val maxStamp: Long = fileStamps.maxBy(_._2)._2
    class CachedClassLoader
        extends URLClassLoader(fileStamps.map(_._1.toURI.toURL).toArray, parent) {
      override def toString: String =
        s"CachedClassloader {\n  parent: $parent\n  urls:\n" + getURLs.mkString("    ", "\n", "\n}")
    }
    def toClassLoader: ClassLoader = new CachedClassLoader
    override def equals(o: Any): Boolean = o match {
      case that: Key => this.fileStamps == that.fileStamps && this.parent == that.parent
    }
    override def hashCode(): Int = (fileStamps.hashCode * 31) ^ parent.hashCode
    override def toString: String = s"Key(${fileStamps mkString ","}, $parent)"
  }
  private[this] val delegate =
    new java.util.concurrent.ConcurrentHashMap[Key, Reference[ClassLoader]]()
  private[this] val referenceQueue = new ReferenceQueue[ClassLoader]

  private[this] def clearExpiredLoaders(): Unit = lock.synchronized {
    val clear = (k: Key, ref: Reference[ClassLoader]) => {
      ref.get() match {
        case w: WrappedLoader => w.invalidate()
        case _                =>
      }
      delegate.remove(k)
      ()
    }
    def isInvalidated(classLoader: ClassLoader): Boolean = classLoader match {
      case w: WrappedLoader => w.invalidated()
      case _                => false
    }
    delegate.asScala.groupBy { case (k, _) => k.parent -> k.files.toSet }.foreach {
      case (_, pairs) if pairs.size > 1 =>
        val max = pairs.map(_._1.maxStamp).max
        pairs.foreach { case (k, v) => if (k.maxStamp != max) clear(k, v) }
      case _ =>
    }
    delegate.forEach((k, v) => if (isInvalidated(k.parent)) clear(k, v))
  }
  private[this] class CleanupThread(private[this] val id: Int)
      extends Thread(s"classloader-cache-cleanup-$id") {
    setDaemon(true)
    start()
    @tailrec
    override final def run(): Unit = {
      val stop = try {
        referenceQueue.remove(1000) match {
          case ClassLoaderReference(key, classLoader) =>
            close(classLoader)
            delegate.remove(key)
            ()
          case _ =>
        }
        clearExpiredLoaders()
        false
      } catch {
        case _: InterruptedException => true
      }
      if (!stop) run()
    }
  }

  /*
   * We need to manage the cache differently depending on whether or not sbt is started up with
   * -XX:MaxMetaspaceSize=XXX. The reason is that when the metaspace limit is reached, the jvm
   * will run a few Full GCs that will clear SoftReferences so that it can cleanup any classes
   * that only softly reachable. If the GC during this phase is able to collect a classloader, it
   * will free the metaspace (or at least some of it) previously occupied by the loader. This can
   * prevent sbt from crashing with an OOM: Metaspace. The issue with this is that when a loader
   * is collected in this way, it will leak handles to its url classpath. To prevent the resource
   * leak, we can store a reference to a wrapper loader. That reference, in turn, holds a
   * strong reference to the underlying loader. Under heap memory pressure, the jvm will clear the
   * soft reference for the wrapped loader and add it to the reference queue. We add a thread
   * that reads from the reference queue and closes the underlying URLClassLoader, preventing the
   * resource leak. When the system is under heap memory pressure, this eviction approach works
   * well. The problem is that we cannot prevent OOM: MetaSpace because the jvm doesn't give us
   * a long enough window to clear the ClassLoader references. The wrapper class will get cleared
   * during the Metaspace Full GC window, but, even though we quickly clear the strong reference
   * to the underlying classloader and close it, the jvm gives up and crashes with an OOM.
   *
   * To avoid these crashes, if the user starts with a limit on metaspace size via
   * -XX:MetaSpaceSize=XXX, we will just store direct soft references to the URLClassLoader and
   * leak url classpath handles when loaders are evicted by garbage collection. This is consistent
   * with the behavior of sbt versions < 1.3.0. In general, these leaks are probably not a big deal
   * except on windows where they prevent any files for which the leaked class loader has an open
   * handle from being modified. On linux and mac, we probably leak some file descriptors but it's
   * fairly uncommon for sbt to run out of file descriptors.
   *
   */
  private[this] val metaspaceIsLimited =
    ManagementFactory.getMemoryPoolMXBeans.asScala
      .exists(b => (b.getName == "Metaspace") && (b.getUsage.getMax > 0))
  private[this] val mkReference: (Key, ClassLoader) => Reference[ClassLoader] =
    if (metaspaceIsLimited) { (_, cl) =>
      (new SoftReference[ClassLoader](cl, referenceQueue): Reference[ClassLoader])
    } else ClassLoaderReference.apply
  private[this] val cleanupThread = new CleanupThread(ClassLoaderCache.threadID.getAndIncrement())
  private[this] val lock = new Object

  private def close(classLoader: ClassLoader): Unit = classLoader match {
    case a: AutoCloseable => a.close()
    case _                =>
  }
  private case class ClassLoaderReference(key: Key, classLoader: ClassLoader)
      extends SoftReference[ClassLoader](
        new WrappedLoader(classLoader),
        referenceQueue
      )
  def apply(
      files: List[(File, Long)],
      parent: ClassLoader,
      mkLoader: () => ClassLoader
  ): ClassLoader = {
    val key = new Key(files, parent)
    get(key, mkLoader)
  }
  override def apply(files: List[File]): ClassLoader = {
    val key = new Key(files)
    get(key, () => key.toClassLoader)
  }
  override def cachedCustomClassloader(
      files: List[File],
      mkLoader: () => ClassLoader
  ): ClassLoader = {
    val key = new Key(files)
    get(key, mkLoader)
  }
  private[this] def get(key: Key, f: () => ClassLoader): ClassLoader = {
    scalaProviderKey match {
      case Some(k) if k == key => k.toClassLoader
      case _ =>
        def addLoader(): ClassLoader = {
          val ref = mkReference(key, f())
          val loader = ref.get
          delegate.put(key, ref)
          clearExpiredLoaders()
          loader
        }
        lock.synchronized {
          delegate.get(key) match {
            case null => addLoader()
            case ref =>
              ref.get match {
                case null => addLoader()
                case l    => l
              }
          }
        }
    }
  }
  private def clear(lock: Object): Unit = {
    delegate.forEach {
      case (_, ClassLoaderReference(_, classLoader)) => close(classLoader)
      case (_, r: Reference[ClassLoader]) =>
        r.get match {
          case null        =>
          case classLoader => close(classLoader)
        }
      case (_, _) =>
    }
    delegate.clear()
  }

  /**
   * Clears any ClassLoader instances from the internal cache and closes them. Calling this
   * method will not stop the cleanup thread. Call [[close]] to fully clean up this cache.
   */
  def clear(): Unit = lock.synchronized(clear(lock))

  /**
   * Completely shuts down this cache. It stops the background thread for cleaning up classloaders
   *
   * Clears any ClassLoader instances from the internal cache and closes them. It also
   * method will not stop the cleanup thread. Call [[close]] to fully clean up this cache.
   */
  override def close(): Unit = lock.synchronized {
    cleanupThread.interrupt()
    cleanupThread.join()
    clear(lock)
  }
}

private[sbt] object AlternativeZincUtil {
  def scalaCompiler(
      scalaInstance: ScalaInstance,
      compilerBridgeJar: File,
      classpathOptions: ClasspathOptions,
      classLoaderCache: Option[IncClassLoaderCache]
  ): AnalyzingCompiler = {
    val bridgeProvider = ZincUtil.constantBridgeProvider(scalaInstance, compilerBridgeJar)
    new AnalyzingCompiler(
      scalaInstance,
      bridgeProvider,
      classpathOptions,
      _ => (),
      classLoaderCache
    )
  }
}
