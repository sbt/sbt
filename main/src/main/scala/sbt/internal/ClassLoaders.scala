/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.{ File, IOException }
import java.net.URL
import java.nio.file.{ FileAlreadyExistsException, Files, FileSystemException, Path }
import java.nio.file.StandardCopyOption.{ ATOMIC_MOVE, COPY_ATTRIBUTES, REPLACE_EXISTING }
import java.util.Locale
import sbt.ClassLoaderLayeringStrategy.*
import sbt.Keys.*
import sbt.internal.classpath.ClassLoaderCache
import sbt.internal.inc.ScalaInstance
import sbt.internal.inc.classpath.ClasspathUtil
import sbt.internal.util.Attributed
import sbt.internal.util.Attributed.data
import sbt.io.IO
import sbt.librarymanagement.ScalaArtifacts
import sbt.nio.FileStamp
import sbt.nio.FileStamp.LastModified
import sbt.nio.Keys.*
import sbt.util.Logger
import xsbti.ArtifactInfo
import xsbti.HashedVirtualFileRef
import scala.collection.mutable

private[sbt] object ClassLoaders:

  private val casBlobName = "[a-z][a-z0-9]*-[0-9a-f]{16,128}-[0-9]+".r
  private val casAliasDirectory = "cas-classloader"

  private[sbt] def toRealURL(f: File): URL =
    val path = f.toPath
    if isClassLoaderAlias(path) then f.toURI.toURL
    else
      val real = try path.toRealPath().toFile
      catch case _: IOException => f
      real.toURI.toURL

  private def isClassLoaderAlias(path: Path): Boolean =
    val parent = path.getParent
    parent != null && Option(parent.getParent).exists(_.endsWith(casAliasDirectory)) &&
    casBlobName.matches(parent.getFileName.toString) && isArchive(path.getFileName.toString)

  private def isArchive(name: String): Boolean =
    val lower = name.toLowerCase(Locale.ROOT)
    lower.endsWith(".jar") || lower.endsWith(".zip")

  private def isCasBlobPath(path: Path): Boolean =
    Option(path.getParent).exists(_.endsWith("cas")) && casBlobName.matches(
      path.getFileName.toString
    )

  private def casBlob(path: Path): Option[Path] =
    try
      val real = path.toRealPath()
      val blob =
        if isCasBlobPath(real) then Some(real)
        else
          val target = Files.readSymbolicLink(path)
          if isCasBlobPath(target.normalize()) then
            val absolute =
              if target.isAbsolute then target
              else path.toAbsolutePath.getParent.toRealPath().resolve(target)
            val logical = absolute.normalize()
            Option.when(Files.isSameFile(real, logical))(logical)
          else None
      blob.filter(Files.isRegularFile(_))
    catch case _: IOException => None

  private[internal] def classLoaderFile(file: File): File =
    val path = file.toPath
    if isArchive(file.getName) && Files.isSymbolicLink(path) && !isClassLoaderAlias(path) then
      casBlob(path) match
        case Some(target) =>
          val alias = target.getParent
            .resolveSibling(casAliasDirectory)
            .resolve(target.getFileName)
            .resolve(path.getFileName)
          if !Files.isRegularFile(alias) then createAlias(alias, target)
          alias.toFile
        case _ => file
    else file

  private def createAlias(alias: Path, blob: Path): Unit =
    Files.createDirectories(alias.getParent)
    try Files.createSymbolicLink(alias, blob)
    catch
      case _: FileAlreadyExistsException if Files.isRegularFile(alias) => ()
      case e: FileAlreadyExistsException                               => throw e
      case _: UnsupportedOperationException | _: FileSystemException   =>
        val temp = Files.createTempFile(alias.getParent, ".classpath-", ".tmp")
        try
          Files.copy(blob, temp, REPLACE_EXISTING, COPY_ATTRIBUTES)
          try Files.move(temp, alias, ATOMIC_MOVE)
          catch case _: FileSystemException if Files.isRegularFile(alias) => ()
        finally Files.deleteIfExists(temp)
    ()

  extension (files: Seq[File]) def urls: Array[URL] = files.toArray.map(toRealURL)
  private val interfaceLoader = classOf[sbt.testing.Framework].getClassLoader
  /*
   * Get the class loader for a test task. The configuration could be Test.
   */
  private[sbt] def testTask: Def.Initialize[Task[ClassLoader]] = Def.task {
    val si = scalaInstance.value
    val useScalaLibraryLayer = autoScalaLibrary.value
    val converter = fileConverter.value
    val cp = fullClasspath.value
      .map(_.data)
      .map(converter.toPath)
      .map(_.toFile)
    val dependencyStamps = modifiedTimes((dependencyClasspathFiles / outputFileStamps).value).toMap
    def getLm(f: File): Long = dependencyStamps.getOrElse(f, IO.getModifiedTimeOrZero(f))
    val rawCP = cp.map(f => f -> getLm(f))
    val fullCP =
      if !useScalaLibraryLayer then rawCP
      else if si.isManagedVersion then rawCP
      else si.libraryJars.map(j => j -> IO.getModifiedTimeOrZero(j)).toSeq ++ rawCP
    val exclude: Set[File] = dependencyJars(exportedProducts).value
      .map(converter.toPath)
      .map(_.toFile)
      .toSet ++ (if useScalaLibraryLayer then si.libraryJars.toSeq else Seq.empty)
    val strategy =
      if useScalaLibraryLayer then classLoaderLayeringStrategy.value
      else ClassLoaderLayeringStrategy.Flat
    val logger = state.value.globalLogging.full
    val close = closeClassLoaders.value
    val allowZombies = allowZombieClassLoaders.value
    buildLayers(
      strategy = strategy,
      si = si,
      fullCP = fullCP,
      allDependenciesSet = dependencyJars(dependencyClasspath).value
        .map(converter.toPath)
        .map(_.toFile)
        .filterNot(exclude)
        .toSet,
      cache = extendedClassLoaderCache.value,
      resources = ClasspathUtil.createClasspathResources(fullCP.map(_._1.toPath), si),
      tmp = IO.createUniqueDirectory(taskTemporaryDirectory.value),
      scope = resolvedScoped.value.scope,
      logger = logger,
      close = close,
      allowZombies = allowZombies,
    )
  }

  private[sbt] def runner: Def.Initialize[Task[ScalaRun]] =
    Def.taskIf {
      if fork.value then
        val s = streams.value
        val options = javaOptions.value
        s.log.debug(s"javaOptions: $options")
        val opts = forkOptions.value
        new ForkRun(opts)
      else
        val converter = fileConverter.value
        val resolvedScope = resolvedScoped.value.scope
        val instance = scalaInstance.value
        val s = streams.value
        val opts = forkOptions.value
        val options = javaOptions.value
        val exclude = dependencyJars(exportedProducts).value
          .map(converter.toPath)
          .map(_.toFile)
          .toSet ++ instance.libraryJars
        val allDeps = dependencyJars(dependencyClasspath).value
          .map(converter.toPath)
          .map(_.toFile)
          .filterNot(exclude)
        val logger = state.value.globalLogging.full
        val allowZombies = allowZombieClassLoaders.value
        val close = closeClassLoaders.value
        val newLoader =
          (cp: Seq[Path]) =>
            val mappings = cp.map(_.toFile()).map(f => f.getName -> f).toMap
            val transformedDependencies = allDeps.map(f => mappings.getOrElse(f.getName, f))
            buildLayers(
              strategy = classLoaderLayeringStrategy.value: @sbtUnchecked,
              si = instance,
              fullCP = cp.map(_.toFile()).map(f => f -> IO.getModifiedTimeOrZero(f)),
              allDependenciesSet = transformedDependencies.toSet,
              cache = extendedClassLoaderCache.value: @sbtUnchecked,
              resources = ClasspathUtil.createClasspathResources(cp, instance),
              tmp = taskTemporaryDirectory.value: @sbtUnchecked,
              scope = resolvedScope,
              logger = logger,
              close = close,
              allowZombies = allowZombies,
            )
        new Run(newLoader, trapExit.value)
    }

  private def extendedClassLoaderCache: Def.Initialize[Task[ClassLoaderCache]] = Def.task {
    val errorMessage = "Tried to extract classloader cache for uninitialized state."
    state.value
      .get(BasicKeys.extendedClassLoaderCache)
      .getOrElse(throw new IllegalStateException(errorMessage))
  }
  /*
   * Create a layered classloader. There are up to four layers:
   * 1) the scala instance class loader (may actually be two layers if scala-reflect is used)
   * 2) the resource layer
   * 3) the dependency jars
   * 4) the rest of the classpath
   * The first three layers may be optionally cached to reduce memory usage and improve
   * start up latency. Because there may be mutually incompatible libraries in the runtime
   * and test dependencies, it's important to be able to configure which layers are used.
   */
  private def buildLayers(
      strategy: ClassLoaderLayeringStrategy,
      si: ScalaInstance,
      fullCP: Seq[(File, Long)],
      allDependenciesSet: Set[File],
      cache: ClassLoaderCache,
      resources: Map[String, String],
      tmp: File,
      scope: Scope,
      logger: Logger,
      close: Boolean,
      allowZombies: Boolean
  ): ClassLoader =
    val cpFiles = fullCP.map(_._1)
    val loaderFiles = mutable.Map.empty[File, File]
    def loaderFile(f: File): File = loaderFiles.getOrElseUpdate(f, classLoaderFile(f))
    strategy match
      case Flat | Raw =>
        val urls = cpFiles.map(loaderFile).urls
        new FlatLoader(urls, interfaceLoader, tmp, close, allowZombies, logger)
      case _ =>
        val layerDependencies = strategy match
          case _: AllLibraryJars => true
          case _                 => false
        val scalaLibraryLayer =
          val jars =
            if ScalaArtifacts.isScala3(si.version) || Classpaths.isScala213(si.version) then
              cpFiles
                .filter(f =>
                  val name = f.getName
                  name == s"${ArtifactInfo.ScalaLibraryID}.jar" ||
                  name.startsWith(s"${ArtifactInfo.ScalaLibraryID}-") ||
                  si.libraryJars.exists(_.getName == name)
                )
                .toArray
            else si.libraryJars
          val loaderJars = jars.map(loaderFile)
          cache.apply(
            loaderJars.map(j => j -> IO.getModifiedTimeOrZero(j)).toList,
            interfaceLoader,
            () => new ScalaLibraryClassLoader(loaderJars.map(_.toURI.toURL), interfaceLoader)
          )

        val allDependencies = cpFiles.filter(allDependenciesSet)
        def isReflectJar(f: File): Boolean =
          f.getName == "scala-reflect.jar" || f.getName.startsWith("scala-reflect-")
        val scalaReflectJar = allDependencies.collectFirst {
          case f if isReflectJar(f) => si.allJars.find(isReflectJar)
        }.flatten
        val scalaReflectLayer = scalaReflectJar
          .map { file =>
            val jar = loaderFile(file)
            cache.apply(
              jar -> IO.getModifiedTimeOrZero(jar) :: Nil,
              scalaLibraryLayer,
              () => new ScalaReflectClassLoader(jar.toURI.toURL, scalaLibraryLayer)
            )
          }
          .getOrElse(scalaLibraryLayer)

        // layer 2 (optional if in the test config and the runtime layer is not shared)
        val dependencyLayer: ClassLoader =
          if layerDependencies && allDependencies.nonEmpty then
            val loaderDependencies = allDependencies.map(loaderFile)
            cache(
              loaderDependencies.toList.map(f => f -> IO.getModifiedTimeOrZero(f)),
              scalaReflectLayer,
              () =>
                new ReverseLookupClassLoaderHolder(
                  classpath = loaderDependencies,
                  parent = scalaReflectLayer,
                  closeThis = close,
                  allowZombies = allowZombies,
                  logger = logger,
                )
            )
          else scalaReflectLayer

        val scalaJarNames = (si.libraryJars ++ scalaReflectJar).map(_.getName).toSet
        // layer 3
        val filteredSet =
          if layerDependencies then allDependencies.toSet ++ si.libraryJars ++ scalaReflectJar
          else Set(si.libraryJars ++ scalaReflectJar*)
        val dynamicClasspath = cpFiles.filterNot(f => filteredSet(f) || scalaJarNames(f.getName))
        dependencyLayer match
          case dl: ReverseLookupClassLoaderHolder =>
            dl.checkout(cpFiles.map(loaderFile), tmp)
          case cl =>
            cl.getParent match
              case dl: ReverseLookupClassLoaderHolder => dl.checkout(cpFiles.map(loaderFile), tmp)
              case _                                  =>
                val urls = dynamicClasspath.map(loaderFile).urls
                new LayeredClassLoader(urls, cl, tmp, close, allowZombies, logger)
    end match
  end buildLayers

  private def dependencyJars(
      key: sbt.TaskKey[Seq[Attributed[HashedVirtualFileRef]]]
  ): Def.Initialize[Task[Seq[HashedVirtualFileRef]]] =
    Def.task(data(key.value).filter(_.id.endsWith(".jar")))

  private def modifiedTimes(stamps: Seq[(Path, FileStamp)]): Seq[(File, Long)] = stamps.map {
    case (p, LastModified(lm)) => p.toFile -> lm
    case (p, _)                =>
      val f = p.toFile
      f -> IO.getModifiedTimeOrZero(f)
  }
end ClassLoaders
