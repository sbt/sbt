package sbt.internal.classpath

import java.io.File
import java.lang.management.ManagementFactory
import java.lang.ref.{ Reference, ReferenceQueue, SoftReference }
import java.net.{ URL, URLClassLoader }
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

  private[this] def closeExpiredLoaders(): Unit = {
    val toClose = lock.synchronized(delegate.asScala.groupBy(_._1.files.toSet).flatMap {
      case (_, pairs) if pairs.size > 1 =>
        val max = pairs.maxBy(_._1.maxStamp)._1
        pairs.filterNot(_._1 == max).flatMap {
          case (k, v) =>
            delegate.remove(k)
            Option(v.get)
        }
      case _ => Nil
    })
    toClose.foreach(close)
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
          case _ =>
        }
        closeExpiredLoaders()
        false
      } catch {
        case _: InterruptedException => true
      }
      if (!stop) run()
    }
  }

  private[this] val metaspaceIsLimited =
    ManagementFactory.getMemoryPoolMXBeans.asScala
      .exists(b => (b.getName == "Metaspace") && (b.getUsage.getMax > 0))
  private[this] val mkReference: (Key, ClassLoader) => Reference[ClassLoader] =
    if (metaspaceIsLimited)(_, cl) => new SoftReference(cl, referenceQueue)
    else ClassLoaderReference.apply
  private[this] val cleanupThread = new CleanupThread(ClassLoaderCache.threadID.getAndIncrement())
  private[this] val lock = new Object

  private class WrappedLoader(parent: ClassLoader) extends URLClassLoader(Array.empty, parent) {
    // This is to make dotty work which extracts the URLs from the loader
    override def getURLs: Array[URL] = parent match {
      case u: URLClassLoader => u.getURLs
      case _                 => Array.empty
    }
    override def toString: String = s"WrappedLoader($parent)"
  }
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
          closeExpiredLoaders()
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
