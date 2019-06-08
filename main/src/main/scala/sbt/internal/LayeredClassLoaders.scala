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

import sbt.internal.inc.classpath._
import sbt.io.IO

import scala.collection.JavaConverters._

/**
 * A simple ClassLoader that copies native libraries to a temporary directory before loading them.
 * Otherwise the same as a normal URLClassLoader.
 * @param classpath the classpath of the url
 * @param parent the parent loader
 * @param tempDir the directory into which native libraries are copied before loading
 */
private[internal] class LayeredClassLoaderImpl(
    classpath: Seq[File],
    parent: ClassLoader,
    tempDir: File
) extends URLClassLoader(classpath.map(_.toURI.toURL).toArray, parent)
    with NativeLoader {
  setTempDir(tempDir)
  override def close(): Unit = if (SysProp.closeClassLoaders) super.close()
}

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
    val parent: ClassLoader
) extends URLClassLoader(Array.empty, null) {
  private[this] val cached: AtomicReference[ReverseLookupClassLoader] = new AtomicReference
  private[this] val closed = new AtomicBoolean(false)

  /**
   * Get a classloader. If there is a loader available in the cache, it will use that loader,
   * otherwise it makes a new classloader.
   *
   * @return a ClassLoader
   */
  def checkout(dependencyClasspath: Seq[File], tempDir: File): ClassLoader = {
    if (closed.get()) {
      val msg = "Tried to extract class loader from closed ReverseLookupClassLoaderHolder. " +
        "Try running the `clearCaches` command and re-trying."
      throw new IllegalStateException(msg)
    }
    val reverseLookupClassLoader = cached.getAndSet(null) match {
      case null => new ReverseLookupClassLoader
      case c    => c
    }
    reverseLookupClassLoader.setTempDir(tempDir)
    new BottomClassLoader(dependencyClasspath, reverseLookupClassLoader, tempDir)
  }

  private def checkin(reverseLookupClassLoader: ReverseLookupClassLoader): Unit = {
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

  /**
   * A ClassLoader for the dependency layer of a run or test task. It is almost a normal
   * URLClassLoader except that it has the ability to look one level down the classloading
   * hierarchy to load a class via reflection that is not directly available to it. The ClassLoader
   * that is below it in the hierarchy must be registered via setDescendant. If it ever loads a
   * class from its descendant, then it cannot be used in a subsequent run because it will not be
   * possible to reload that class.
   *
   * The descendant classloader checks it in and out via [[checkout]] and [[checkin]]. When it
   * returns the loader via [[checkin]], if the loader is dirty, we close it. Otherwise we
   * cache it if there is no existing cache entry.
   *
   * Performance degrades if loadClass is constantly looking back up to the provided
   * BottomClassLoader so we provide an alternate loadClass definition that takes a reverseLookup
   * boolean parameter. Because the [[BottomClassLoader]] knows what loader is calling into, when
   * it delegates its search to the ReverseLookupClassLoader, it passes false for the reverseLookup
   * flag. By default the flag is true. Most of the time, the default loadClass will only be
   * invoked by java reflection calls. Even then, there's some chance that the class being loaded
   * by java reflection is _not_ available on the bottom classpath so it is not guaranteed that
   * performing the reverse lookup will invalidate this loader.
   *
   */
  private class ReverseLookupClassLoader
      extends URLClassLoader(classpath.map(_.toURI.toURL).toArray, parent)
      with NativeLoader {
    private[this] val directDescendant: AtomicReference[BottomClassLoader] =
      new AtomicReference
    private[this] val dirty = new AtomicBoolean(false)
    def isDirty: Boolean = dirty.get()
    def setDescendant(classLoader: BottomClassLoader): Unit = directDescendant.set(classLoader)
    def loadClass(name: String, resolve: Boolean, reverseLookup: Boolean): Class[_] = {
      try super.loadClass(name, resolve)
      catch {
        case e: ClassNotFoundException if reverseLookup =>
          directDescendant.get match {
            case null => throw e
            case cl =>
              val res = cl.lookupClass(name)
              dirty.set(true)
              res
          }
      }
    }
    override def loadClass(name: String, resolve: Boolean): Class[_] =
      loadClass(name, resolve, reverseLookup = true)
    override def close(): Unit = if (SysProp.closeClassLoaders) super.close()
  }

  /**
   * The bottom most layer in our layering hierarchy. This layer should never be cached. The
   * dependency layer may need access to classes only available at this layer using java
   * reflection. To make this work, we register this loader with the parent in its
   * constructor. We also add the lookupClass method which gives ReverseLookupClassLoader
   * a public interface to findClass.
   *
   * To improve performance, when loading classes from the parent, we call the loadClass
   * method with the reverseLookup flag set to false. This prevents the ReverseLookupClassLoader
   * from trying to call back into this loader when it can't find a particular class.
   *
   * @param dynamicClasspath the classpath for the run or test task excluding the dependency jars
   * @param parent the ReverseLookupClassLoader with which this loader needs to register itself
   *               so that reverse lookups required by java reflection will work
   * @param tempDir the temp directory to copy native libraries
   */
  private class BottomClassLoader(
      dynamicClasspath: Seq[File],
      parent: ReverseLookupClassLoader,
      tempDir: File
  ) extends URLClassLoader(dynamicClasspath.map(_.toURI.toURL).toArray, parent)
      with NativeLoader {
    parent.setDescendant(this)
    setTempDir(tempDir)

    final def lookupClass(name: String): Class[_] = findClass(name)

    override def findClass(name: String): Class[_] = findLoadedClass(name) match {
      case null => super.findClass(name)
      case c    => c
    }
    override def loadClass(name: String, resolve: Boolean): Class[_] = {
      val clazz = findLoadedClass(name) match {
        case null =>
          val c = try parent.loadClass(name, resolve = false, reverseLookup = false)
          catch { case _: ClassNotFoundException => findClass(name) }
          if (resolve) resolveClass(c)
          c
        case c => c
      }
      if (resolve) resolveClass(clazz)
      clazz
    }
    override def close(): Unit = {
      checkin(parent)
      if (SysProp.closeClassLoaders) super.close()
    }
  }
}

/**
 * This is more or less copied from the NativeCopyLoader in zinc. It differs from the zinc
 * NativeCopyLoader in that it doesn't allow any explicit mappings and it allows the tempDir
 * to be dynamically reset. The explicit mappings feature isn't used by sbt. The dynamic
 * temp directory use case is needed in some layered class loading scenarios.
 */
private trait NativeLoader extends ClassLoader with AutoCloseable {
  private[this] val mapped = new ConcurrentHashMap[String, String]
  private[this] val searchPaths =
    sys.props.get("java.library.path").map(IO.parseClasspath).getOrElse(Nil)
  private[this] val tempDir = new AtomicReference(new File("/dev/null"))

  abstract override def close(): Unit = {
    setTempDir(new File("/dev/null"))
    super.close()
  }
  override protected def findLibrary(name: String): String = synchronized {
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
  private[internal] def setTempDir(file: File): Unit = {
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

private[internal] class ResourceLoaderImpl(
    classpath: Seq[File],
    parent: ClassLoader,
    override val resources: Map[String, String]
) extends URLClassLoader(classpath.map(_.toURI.toURL).toArray, parent)
    with RawResources {
  override def findClass(name: String): Class[_] = throw new ClassNotFoundException(name)
  override def loadClass(name: String, resolve: Boolean): Class[_] = {
    val clazz = parent.loadClass(name)
    if (resolve) resolveClass(clazz)
    clazz
  }
  override def toString: String = "ResourceLoader"
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
