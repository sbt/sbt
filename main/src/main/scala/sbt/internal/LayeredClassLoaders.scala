/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }

import sbt.io.IO
import sbt.util.Logger

import scala.collection.JavaConverters._

/**
 * This classloader doesn't load any classes. It is able to create a two layer bundled ClassLoader
 * that is able to load the full project classpath. The top layer is able to directly load the
 * project dependencies. The bottom layer can load the full classpath of the run or test task.
 * If the top layer needs to load a class from the bottom layer via java reflection, we facilitate
 * that with the `ReverseLookupClassLoader`.
 *
 *
 * This holder caches the ReverseLookupClassLoader, which is the top loader in this hierarchy. The
 * checkout method will get the RevereLookupClassLoader from the cache or make a new one if
 * none is available. It will only cache at most one so if multiple concurrently tasks have the
 * same dependency classpath, multiple instances of ReverseLookupClassLoader will be created for
 * the classpath. If the ReverseLookupClassLoader makes a lookup in the BottomClassLoader, it
 * invalidates itself and will not be cached when it is returned.
 *
 * The reason it is a ClassLoader even though it can't load any classes is so its
 * lifecycle -- and therefore the lifecycle of its cache entry -- is managed by the
 * ClassLoaderCache, allowing the cache entry to be evicted under memory pressure.
 *
 * @param classpath the dependency classpath of the managed loader
 * @param parent the parent ClassLoader of the managed loader
 */
private[internal] final class ReverseLookupClassLoaderHolder(
    val classpath: Seq[File],
    val parent: ClassLoader,
    val closeThis: Boolean,
    val allowZombies: Boolean,
    val logger: Logger
) extends URLClassLoader(Array.empty, null) {
  private[this] val cached: AtomicReference[ReverseLookupClassLoader] = new AtomicReference
  private[this] val closed = new AtomicBoolean(false)
  private[this] val urls = classpath.map(_.toURI.toURL).toArray

  /**
   * Get a classloader. If there is a loader available in the cache, it will use that loader,
   * otherwise it makes a new classloader.
   *
   * @return a ClassLoader
   */
  def checkout(fullClasspath: Seq[File], tempDir: File): ClassLoader = {
    if (closed.get()) {
      val msg = "Tried to extract class loader from closed ReverseLookupClassLoaderHolder. " +
        "Try running the `clearCaches` command and re-trying."
      throw new IllegalStateException(msg)
    }
    val reverseLookupClassLoader = cached.getAndSet(null) match {
      case null => new ReverseLookupClassLoader(urls, parent, closeThis, allowZombies, logger)
      case c    => c
    }
    reverseLookupClassLoader.setup(tempDir, fullClasspath.map(_.toURI.toURL).toArray)
    new BottomClassLoader(
      ReverseLookupClassLoaderHolder.this,
      fullClasspath.map(_.toURI.toURL).toArray,
      reverseLookupClassLoader,
      tempDir,
      closeThis,
      allowZombies,
      logger
    )
  }

  private[sbt] def checkin(reverseLookupClassLoader: ReverseLookupClassLoader): Unit = {
    if (reverseLookupClassLoader.isDirty) reverseLookupClassLoader.close()
    else {
      if (closed.get()) reverseLookupClassLoader.close()
      else
        cached.getAndSet(reverseLookupClassLoader) match {
          case null => if (closed.get) reverseLookupClassLoader.close()
          case c    => c.close()
        }
    }
  }

  override def close(): Unit = {
    closed.set(true)
    cached.get() match {
      case null =>
      case c    => c.close()
    }
  }
}

/**
 * This is more or less copied from the NativeCopyLoader in zinc. It differs from the zinc
 * NativeCopyLoader in that it doesn't allow any explicit mappings and it allows the tempDir
 * to be dynamically reset. The explicit mappings feature isn't used by sbt. The dynamic
 * temp directory use case is needed in some layered class loading scenarios.
 */
private[internal] trait NativeLoader extends AutoCloseable {
  private[internal] def setTempDir(file: File): Unit = {}
}
private[internal] class NativeLookup extends NativeLoader {
  private[this] val mapped = new ConcurrentHashMap[String, String]
  private[this] val searchPaths =
    sys.props.get("java.library.path").map(IO.parseClasspath).getOrElse(Nil)
  private[this] val tempDir = new AtomicReference(new File("/dev/null"))

  override def close(): Unit = setTempDir(new File("/dev/null"))

  def findLibrary(name: String): String = synchronized {
    mapped.get(name) match {
      case null =>
        findLibrary0(name) match {
          case null => null
          case n =>
            mapped.put(name, n)
            NativeLibs.addNativeLib(n)
            n
        }
      case n => n
    }
  }

  private[internal] override def setTempDir(file: File): Unit = {
    deleteNativeLibs()
    tempDir.set(file)
  }

  private[this] def deleteNativeLibs(): Unit = {
    mapped.values().forEach(NativeLibs.delete)
    mapped.clear()
  }

  private[this] def findLibrary0(name: String): String = {
    val mappedName = System.mapLibraryName(name)
    val search = searchPaths.toStream flatMap relativeLibrary(mappedName)
    search.headOption.map(copy).orNull
  }

  private[this] def relativeLibrary(mappedName: String)(base: File): Seq[File] = {
    val f = new File(base, mappedName)
    if (f.isFile) f :: Nil else Nil
  }

  private[this] def copy(f: File): String = {
    val target = new File(tempDir.get(), f.getName)
    IO.copyFile(f, target)
    target.getAbsolutePath
  }
}

private[internal] object NativeLibs {
  private[this] val nativeLibs = new java.util.HashSet[File].asScala
  ShutdownHooks.add(() => {
    nativeLibs.foreach(IO.delete)
    IO.deleteIfEmpty(nativeLibs.map(_.getParentFile).toSet)
    nativeLibs.clear()
  })
  def addNativeLib(lib: String): Unit = {
    nativeLibs.add(new File(lib))
    ()
  }
  def delete(lib: String): Unit = {
    val file = new File(lib)
    nativeLibs.remove(file)
    file.delete()
    ()
  }
}
