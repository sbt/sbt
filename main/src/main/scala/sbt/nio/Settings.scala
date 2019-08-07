/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package nio

import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

import sbt.Project._
import sbt.internal.Clean.ToSeqPath
import sbt.internal.Continuous.FileStampRepository
import sbt.internal.util.{ AttributeKey, SourcePosition }
import sbt.internal.{ Clean, Continuous, DynamicInput, SettingsGraph }
import sbt.nio.FileStamp.Formats._
import sbt.nio.FileStamper.{ Hash, LastModified }
import sbt.nio.Keys._
import sbt.nio.file.{ AllPass, FileAttributes }
import sbt.std.TaskExtra._
import sjsonnew.JsonFormat

import scala.collection.JavaConverters._
import scala.collection.immutable.VectorBuilder
import scala.collection.mutable

private[sbt] object Settings {
  private[sbt] def inject(transformed: Seq[Def.Setting[_]]): Seq[Def.Setting[_]] = {
    val fileOutputScopes = transformed.collect {
      case s if s.key.key == sbt.nio.Keys.fileOutputs.key && s.key.scope.task.toOption.isDefined =>
        s.key.scope
    }.toSet
    val cleanScopes = new java.util.HashSet[Scope].asScala
    transformed.flatMap {
      case s if s.key.key == sbt.nio.Keys.fileInputs.key => inputPathSettings(s)
      case s                                             => s :: maybeAddOutputsAndFileStamps(s, fileOutputScopes, cleanScopes)
    } ++ addCleanImpls(cleanScopes.toSeq)
  }

  /**
   * This method checks if the setting is for a task with a return type in:
   * `File`, `Seq[File]`, `Path`, `Seq[Path`. If it does, then we inject a number of
   * task definition settings that allow the user to check if the output paths of
   * the task have changed. It also adds a custom clean task that will delete the
   * paths returned by the task, provided that they are in the task's target directory. We also
   * inject these tasks if the fileOutputs setting is defined for the task.
   *
   * @param setting the setting to possibly inject with additional settings
   * @param fileOutputScopes the set of scopes for which the fileOutputs setting is defined
   * @param cleanScopes the set of cleanScopes that we may add this setting's scope
   * @return the injected settings
   */
  private[this] def maybeAddOutputsAndFileStamps(
      setting: Def.Setting[_],
      fileOutputScopes: Set[Scope],
      cleanScopes: mutable.Set[Scope]
  ): List[Def.Setting[_]] = {
    setting.key.key match {
      case ak: AttributeKey[_] if taskClass.isAssignableFrom(ak.manifest.runtimeClass) =>
        def default: List[Def.Setting[_]] = {
          val scope = setting.key.scope.copy(task = Select(ak))
          if (fileOutputScopes.contains(scope)) {
            val sk = setting.asInstanceOf[Def.Setting[Task[Any]]].key
            val scopedKey = Keys.dynamicFileOutputs in (sk.scope in sk.key)
            cleanScopes.add(scope)
            addTaskDefinition {
              val init: Def.Initialize[Task[Seq[Path]]] = sk(_.map(_ => Nil))
              Def.setting[Task[Seq[Path]]](scopedKey, init, setting.pos)
            } :: allOutputPathsImpl(scope) :: outputFileStampsImpl(scope) :: cleanImpl(scope) :: Nil
          } else Nil
        }
        def mkSetting[T: JsonFormat: ToSeqPath]: List[Def.Setting[_]] = {
          val sk = setting.asInstanceOf[Def.Setting[Task[T]]].key
          val taskKey = TaskKey(sk.key) in sk.scope
          // We create a previous reference so that clean automatically works without the
          // user having to explicitly call previous anywhere.
          val init = Previous.runtime(taskKey).zip(taskKey) {
            case (_, t) => t.map(implicitly[ToSeqPath[T]].apply)
          }
          val key = Def.ScopedKey(taskKey.scope in taskKey.key, Keys.dynamicFileOutputs.key)
          addTaskDefinition(Def.setting[Task[Seq[Path]]](key, init, setting.pos)) ::
            outputsAndStamps(taskKey, cleanScopes)
        }
        ak.manifest.typeArguments match {
          case t :: Nil if seqClass.isAssignableFrom(t.runtimeClass) =>
            t.typeArguments match {
              case f :: Nil if fileClass.isAssignableFrom(f.runtimeClass) => mkSetting[Seq[File]]
              case p :: Nil if pathClass.isAssignableFrom(p.runtimeClass) => mkSetting[Seq[Path]]
              case _                                                      => default
            }
          case t :: Nil if fileClass.isAssignableFrom(t.runtimeClass) => mkSetting[File]
          case t :: Nil if pathClass.isAssignableFrom(t.runtimeClass) => mkSetting[Path]
          case _                                                      => default
        }
      case _ => Nil
    }
  }
  private[sbt] val inject: Def.ScopedKey[_] => Seq[Def.Setting[_]] = scopedKey =>
    scopedKey.key match {
      case transitiveDynamicInputs.key =>
        scopedKey.scope.task.toOption.toSeq.map { key =>
          val updatedKey = Def.ScopedKey(scopedKey.scope.copy(task = Zero), key)
          transitiveDynamicInputs in scopedKey.scope := SettingsGraph.task(updatedKey).value
        }
      case dynamicDependency.key => (dynamicDependency in scopedKey.scope := { () }) :: Nil
      case transitiveClasspathDependency.key =>
        (transitiveClasspathDependency in scopedKey.scope := { () }) :: Nil
      case _ => Nil
    }

  /**
   * This method collects all of the automatically generated clean tasks and adds each of them
   * to the clean method scoped by project/config or just project
   *
   * @param scopes the clean scopes that have been automatically generated
   * @return the custom clean tasks
   */
  private[this] def addCleanImpls(scopes: Seq[Scope]): Seq[Def.Setting[_]] = {
    val configScopes = scopes.groupBy(scope => scope.copy(task = Zero))
    val projectScopes = scopes.groupBy(scope => scope.copy(task = Zero, config = Zero))
    (configScopes ++ projectScopes).map {
      case (scope, cleanScopes) =>
        val dependentKeys = cleanScopes.map(sbt.Keys.clean.in)
        Def.setting(
          sbt.Keys.clean in scope,
          (sbt.Keys.clean in scope).dependsOn(dependentKeys: _*).tag(Tags.Clean),
          SourcePosition.fromEnclosing()
        )
    }.toVector
  }

  /**
   * This adds the [[sbt.Keys.taskDefinitionKey]] to the work for each [[Task]]. Without
   * this, the previous macro doesn't work correctly because [[Previous]] is unable to
   * reference the task.
   *
   * @param setting the [[Def.Setting[_}]] for which we add the task definition
   * @tparam T the generic type of the task (needed for type checking because [[Task]] is invariant)
   * @return the setting with the task definition
   */
  private[this] def addTaskDefinition[T](setting: Def.Setting[Task[T]]): Def.Setting[Task[T]] =
    setting.mapInit((sk, task) => Task(task.info.set(sbt.Keys.taskDefinitionKey, sk), task.work))

  /**
   * Returns all of the paths described by a glob along with their basic file attributes.
   * No additional filtering is performed.
   *
   * @param setting the setting whose fileInputs we are seeking
   * @return a task definition that retrieves the file input files and their attributes scoped
   *         to a particular task.
   */
  private[sbt] def inputPathSettings(setting: Def.Setting[_]): Seq[Def.Setting[_]] = {
    val scopedKey = setting.key
    val scope = scopedKey.scope
    setting :: (Keys.allInputPathsAndAttributes in scope := {
      val view = (fileTreeView in scope).value
      val inputs = (fileInputs in scope).value
      val stamper = (inputFileStamper in scope).value
      val forceTrigger = (watchForceTriggerOnAnyChange in scope).value
      val dynamicInputs = (Continuous.dynamicInputs in scope).value
      // This makes watch work by ensuring that the input glob is registered with the
      // repository used by the watch process.
      sbt.Keys.state.value.get(globalFileTreeRepository).foreach { repo =>
        inputs.foreach(repo.register)
      }
      dynamicInputs.foreach(_ ++= inputs.map(g => DynamicInput(g, stamper, forceTrigger)))
      view.list(inputs)
    }) :: fileStamps(scopedKey) :: allFilesImpl(scope) :: changedInputFilesImpl(scope)
  }

  private[this] val taskClass = classOf[Task[_]]
  private[this] val seqClass = classOf[Seq[_]]
  private[this] val fileClass = classOf[java.io.File]
  private[this] val pathClass = classOf[java.nio.file.Path]

  /**
   * Returns all of the paths for the regular files described by a glob. Directories and hidden
   * files are excluded.
   *
   * @param scopedKey the key whose file inputs we are seeking
   * @return a task definition that retrieves all of the input paths scoped to the input key.
   */
  private[this] def allFilesImpl(scope: Scope): Def.Setting[_] = {
    addTaskDefinition(Keys.allInputFiles in scope := {
      val filter =
        (fileInputIncludeFilter in scope).value && !(fileInputExcludeFilter in scope).value
      (Keys.allInputPathsAndAttributes in scope).value.collect {
        case (p, a) if filter.accept(p, a) => p
      }
    })
  }

  /**
   * Returns all of the regular files whose stamp has changed since the last time the
   * task was evaluated. The result includes new and modified files but not deleted
   * files or files whose stamp has not changed since the previous run. Directories and hidden
   * files are excluded
   *
   * @param scope the scope corresponding to the task whose fileInputs we are seeking
   * @return a task definition that retrieves the changed input files scoped to the key.
   */
  private[this] def changedInputFilesImpl(scope: Scope): List[Def.Setting[_]] =
    changedFilesImpl(scope, changedInputFiles, inputFileStamps) ::
      (watchForceTriggerOnAnyChange in scope := {
        (watchForceTriggerOnAnyChange in scope).?.value match {
          case Some(t) => t
          case None    => false
        }
      }) :: Nil
  private[this] def changedFilesImpl(
      scope: Scope,
      changeKey: TaskKey[Seq[(Path, FileStamp)] => FileChanges],
      stampKey: TaskKey[Seq[(Path, FileStamp)]]
  ): Def.Setting[_] =
    addTaskDefinition(changeKey in scope := {
      val current = (stampKey in scope).value
      changedFiles(_, current)
    })
  private[sbt] def changedFiles(
      previous: Seq[(Path, FileStamp)],
      current: Seq[(Path, FileStamp)]
  ): FileChanges = {
    val createdBuilder = new VectorBuilder[Path]
    val deletedBuilder = new VectorBuilder[Path]
    val modifiedBuilder = new VectorBuilder[Path]
    val unmodifiedBuilder = new VectorBuilder[Path]
    val seen = ConcurrentHashMap.newKeySet[Path]
    val prevMap = new ConcurrentHashMap[Path, FileStamp]()
    previous.foreach { case (k, v) => prevMap.put(k, v); () }
    current.foreach {
      case (path, currentStamp) =>
        if (seen.add(path)) {
          prevMap.remove(path) match {
            case null => createdBuilder += path
            case old  => (if (old != currentStamp) modifiedBuilder else unmodifiedBuilder) += path
          }
        }
    }
    prevMap.forEach((p, _) => deletedBuilder += p)
    val unmodified = unmodifiedBuilder.result()
    if (unmodified.size == current.size) {
      FileChanges.unmodified(unmodifiedBuilder.result)
    } else {
      val created = createdBuilder.result()
      val deleted = deletedBuilder.result()
      val modified = modifiedBuilder.result()
      FileChanges(created, deleted, modified, unmodified)
    }
  }

  /**
   * Provides an automatically generated clean method for a task that provides fileOutputs.
   *
   * @param scope the scope to add the custom clean
   * @return a task specific clean implementation
   */
  private[sbt] def cleanImpl(scope: Scope): Def.Setting[_] = addTaskDefinition {
    sbt.Keys.clean in scope := Clean.task(scope, full = false).value
  }

  /**
   * Provides an automatically generated clean method for a task that provides fileOutputs.
   *
   * @param taskKey the task for which we add a custom clean implementation
   * @return a task specificic clean implementation
   */
  private[sbt] def cleanImpl[T: JsonFormat: ToSeqPath](taskKey: TaskKey[T]): Def.Setting[_] = {
    val taskScope = taskKey.scope in taskKey.key
    addTaskDefinition(sbt.Keys.clean in taskScope := Def.taskDyn {
      // the clean file task needs to run first because the previous cache gets blown away
      // by the second task
      Clean.cleanFileOutputTask(taskKey).value
      Clean.task(taskScope, full = false)
    }.value)
  }

  /**
   * Returns all of the regular files and the corresponding file stamps for the file inputs
   * scoped to the input key. Directories and hidden files are excluded.
   *
   * @param scopedKey the key whose fileInputs we are seeking
   * @return a task definition that retrieves the input files and their file stamps scoped to the
   *         input key.
   */
  private[sbt] def fileStamps(scopedKey: Def.ScopedKey[_]): Def.Setting[_] = {
    val scope = scopedKey.scope
    addTaskDefinition(Keys.inputFileStamps in scope := {
      val cache = (unmanagedFileStampCache in scope).value
      val stamper = (Keys.inputFileStamper in scope).value
      val stampFile: Path => Option[(Path, FileStamp)] =
        sbt.Keys.state.value.get(globalFileTreeRepository) match {
          case Some(repo: FileStampRepository) =>
            (path: Path) =>
              repo.putIfAbsent(path, stamper) match {
                case (None, Some(s)) =>
                  cache.put(path, s)
                  Some(path -> s)
                case _ => cache.getOrElseUpdate(path, stamper).map(path -> _)
              }
          case _ =>
            (path: Path) => cache.getOrElseUpdate(path, stamper).map(path -> _)
        }
      val filter =
        (fileInputIncludeFilter in scope).value && !(fileInputExcludeFilter in scope).value
      (Keys.allInputPathsAndAttributes in scope).value.flatMap {
        case (path, a) if filter.accept(path, a) => stampFile(path)
        case _                                   => None
      }
    })
  }

  private[this] def outputsAndStamps[T: JsonFormat: ToSeqPath](
      taskKey: TaskKey[T],
      cleanScopes: mutable.Set[Scope]
  ): List[Def.Setting[_]] = {
    val scope = taskKey.scope in taskKey.key
    cleanScopes.add(scope)
    val changes = changedFilesImpl(scope, changedOutputFiles, outputFileStamps) :: Nil
    allOutputPathsImpl(scope) :: outputFileStampsImpl(scope) :: cleanImpl(taskKey) :: changes
  }
  private[this] def allOutputPathsImpl(scope: Scope): Def.Setting[_] =
    addTaskDefinition(allOutputFiles in scope := {
      val filter =
        (fileOutputIncludeFilter in scope).value && !(fileOutputExcludeFilter in scope).value
      val fileOutputGlobs = (fileOutputs in scope).value
      val allFileOutputs = (fileTreeView in scope).value.list(fileOutputGlobs).map(_._1)
      val dynamicOutputs = (dynamicFileOutputs in scope).value
      /*
       * We want to avoid computing the FileAttributes in the common case where nothing is
       * being filtered (which is the case with the default filters:
       * include = AllPass, exclude = NoPass).
       */
      val attributeFilter: Path => Boolean = filter match {
        case AllPass => _ => true
        case f       => p => FileAttributes(p).map(f.accept(p, _)).getOrElse(false)
      }
      allFileOutputs ++ dynamicOutputs.filterNot { p =>
        fileOutputGlobs.exists(_.matches(p)) || !attributeFilter(p)
      }
    })
  private[this] def outputFileStampsImpl(scope: Scope): Def.Setting[_] =
    addTaskDefinition(outputFileStamps in scope := {
      val stamper: Path => Option[FileStamp] = (outputFileStamper in scope).value match {
        case LastModified => FileStamp.lastModified
        case Hash         => FileStamp.hash
      }
      (allOutputFiles in scope).value.flatMap(p => stamper(p).map(p -> _))
    })

}
