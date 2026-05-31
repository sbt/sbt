/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.IOException
import java.nio.file.{ DirectoryNotEmptyException, Files, Path }

import sbt.BasicCommandStrings.*
import sbt.Def.*
import sbt.Keys.*
// import sbt.Project.richInitializeTask
import sbt.ProjectExtra.*
import sbt.ScopeAxis.Zero
import sbt.io.syntax.*
import sbt.io.IO
import sbt.nio.Keys.*
import sbt.nio.file.*
import sbt.nio.file.syntax.pathToPathOps
import sbt.nio.file.Glob.GlobOps
import sbt.util.{ DiskActionCacheStore, Level }
import sbt.internal.util.complete.SizeParser
import sjsonnew.JsonFormat
import xsbti.{ PathBasedFile, VirtualFileRef }
import xsbti.compile.CompilerCache

private[sbt] object Clean {

  private[sbt] def deleteContents(file: File, exclude: File => Boolean): Unit =
    deleteContents(
      file.toPath,
      path => exclude(path.toFile),
      FileTreeView.default,
      tryDelete((_: String) => {})
    )
  private def deleteContents(
      path: Path,
      exclude: Path => Boolean,
      view: FileTreeView.Nio[FileAttributes],
      delete: Path => Unit
  ): Unit = {
    def deleteRecursive(path: Path): Unit = {
      view
        .list(Glob(path, AnyPath))
        .filterNot { case (p, _) => exclude(p) }
        .foreach {
          case (dir, attrs) if attrs.isDirectory && !attrs.isSymbolicLink =>
            deleteRecursive(dir)
            delete(dir)
          case (file, _) => delete(file)
        }
    }
    deleteRecursive(path)
  }

  private def cleanFilter(scope: Scope): Def.Initialize[Task[Path => Boolean]] = Def.task {
    val excludes = (scope / cleanKeepFiles).value.map {
      // This mimics the legacy behavior of cleanFilesTask
      case f if f.isDirectory => Glob(f, AnyPath)
      case f                  => f.toPath.toGlob
    } ++ (scope / cleanKeepGlobs).value
    (p: Path) => excludes.exists(_.matches(p))
  }
  private def cleanDelete(scope: Scope): Def.Initialize[Task[Path => Unit]] = Def.task {
    // Don't use a regular logger because the logger actually writes to the target directory.
    val debug = (scope / logLevel).?.value.orElse(state.value.get(logLevel.key)) match {
      case Some(Level.Debug) =>
        (string: String) => println(s"[debug] $string")
      case _ =>
        (_: String) => {}
    }
    tryDelete(debug)
  }

  private[sbt] def scopedTask: Def.Initialize[Task[Unit]] =
    Keys.resolvedScoped.toTaskable.toTask.flatMapTask { case (r: ScopedKey[?]) =>
      task(r.scope, full = true)
    }

  /**
   * Implements the clean task in a given scope. It uses the outputs task value in the provided
   * scope to determine which files to delete.
   *
   * @param scope the scope in which the clean task is implemented
   * @return the clean task definition.
   */
  private[sbt] def task(
      scope: Scope,
      full: Boolean
  ): Def.Initialize[Task[Unit]] =
    (Def
      .task {
        val state = Keys.state.value
        val extracted = Project.extract(state)
        val view = (scope / fileTreeView).value
        val manager = streamsManager.value
        (state, extracted, view, manager)
      })
      .flatMapTask { (state, extracted, view, manager) =>
        Def.task {
          val excludeFilter = cleanFilter(scope).value
          val delete = cleanDelete(scope).value
          val targetDir = (scope / target).?.value.map(_.toPath)

          targetDir.withFilter(_ => full).foreach(deleteContents(_, excludeFilter, view, delete))
          (scope / cleanFiles).?.value.getOrElse(Nil).foreach { x =>
            if (x.isDirectory) deleteContents(x.toPath, excludeFilter, view, delete)
            else delete(x.toPath)
          }

          // This is the special portion of the task where we clear out the relevant streams
          // and file outputs of a task.
          val streamsKey = scope.task.toOption.map(k => ScopedKey(scope.copy(task = Zero), k))
          val stampKey = ScopedKey(scope, inputFileStamps.key)
          val stampsKey =
            if extracted.structure.data.contains(stampKey) then stampKey :: Nil else Nil
          val streamsGlobs =
            (streamsKey.toSeq ++ stampsKey)
              .map(k => manager(k).cacheDirectory.toPath.toGlob / **)
          ((scope / fileOutputs).value.filter { g =>
            targetDir.fold(true)(g.base.startsWith)
          } ++ streamsGlobs)
            .foreach { g =>
              val filter: Path => Boolean = { path =>
                !g.matches(path) || excludeFilter(path)
              }
              deleteContents(g.base, filter, FileTreeView.default, delete)
              delete(g.base)
            }
        }
      }
      .tag(Tags.Clean)

  // SAM
  private[sbt] trait ToSeqPath[A]:
    def apply(a: A): Seq[Path]
  end ToSeqPath

  private[sbt] object ToSeqPath:
    given identitySeqPath: ToSeqPath[Seq[Path]] = identity[Seq[Path]](_)
    given seqFile: ToSeqPath[Seq[File]] = _.map(_.toPath)
    given virtualFileRefSeq: ToSeqPath[Seq[VirtualFileRef]] =
      _.collect { case f: PathBasedFile => f.toPath }
    given path: ToSeqPath[Path] = _ :: Nil
    given file: ToSeqPath[File] = _.toPath :: Nil
    given virtualFileRef: ToSeqPath[VirtualFileRef] =
      case f: PathBasedFile => Seq(f.toPath)
      case _                => Nil
  end ToSeqPath

  extension [T](t: T) {
    private def toSeqPath(using toSeqPath: ToSeqPath[T]): Seq[Path] = toSeqPath(t)
  }

  private[sbt] def cleanFileOutputTask[T: JsonFormat: ToSeqPath](
      taskKey: TaskKey[T]
  ): Def.Initialize[Task[Unit]] =
    (Def
      .task {
        taskKey.scope.rescope(taskKey.key)
      })
      .flatMapTask { case scope =>
        Def.task {
          val targetDir = (scope / target).value.toPath
          val filter = cleanFilter(scope).value
          // We do not want to inadvertently delete files that are not in the target directory.
          val excludeFilter: Path => Boolean = path => !path.startsWith(targetDir) || filter(path)
          val delete = cleanDelete(scope).value
          val st = (scope / streams).value
          taskKey.previous.foreach(_.toSeqPath.foreach(p => if (!excludeFilter(p)) delete(p)))
          delete(st.cacheDirectory.toPath / Previous.DependencyDirectory)
        }
      }
      .tag(Tags.Clean)

  private def tryDelete(debug: String => Unit): Path => Unit = path => {
    try {
      debug(s"clean -- deleting file $path")
      Files.deleteIfExists(path)
      ()
    } catch {
      case _: DirectoryNotEmptyException =>
        debug(s"clean -- unable to delete non-empty directory $path")
      case e: IOException =>
        debug(s"Caught unexpected exception $e deleting $path")
    }
  }

  val registerCompilerCache: State => State = (s: State) =>
    s.get(Keys.stateCompilerCache).foreach(_.clear())
    s.put(Keys.stateCompilerCache, CompilerCache.fresh)

  val addCacheStoreFactoryFactory: State => State = (s: State) =>
    val size = Project
      .extract(s)
      .getOpt(Keys.fileCacheSize)
      .flatMap(SizeParser(_))
      .getOrElse(SysProp.fileCacheSize)
    s.get(Keys.cacheStoreFactoryFactory).foreach(_.close())
    s.put(Keys.cacheStoreFactoryFactory, InMemoryCacheStore.factory(size))

  private val clearCachesFun: State => State =
    registerCompilerCache
      .andThen(_.initializeClassLoaderCache)
      .andThen(addCacheStoreFactoryFactory)

  def clearCaches: Command =
    val help = Help.more(ClearCaches, ClearCachesDetailed)
    Command.command(ClearCaches, help)(clearCachesFun)

  def cleanFull: Command =
    val h = Help.more(CleanFull, cleanFullDetailed)
    val expunge: State => State =
      (s: State) =>
        val outputDirectory = s
          .get(BasicKeys.rootOutputDirectory)
          .getOrElse(sys.error("outputDirectory has not been set"))
        val cacheStore = s.get(BasicKeys.cacheStores).getOrElse(Nil)
        cacheStore.foreach:
          case d: DiskActionCacheStore => d.clear()
          case _                       => ()
        IO.delete(outputDirectory.toFile())
        IO.delete(
          s.configuration
            .provider()
            .scalaProvider()
            .launcher()
            .bootDirectory()
        )
        s
    Command.command(CleanFull, h)(expunge andThen clearCachesFun)
}
