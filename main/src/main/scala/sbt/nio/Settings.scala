/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package nio

import java.io.File
import java.nio.file.{ Files, Path }

import sbt.Project._
import sbt.internal.Clean.ToSeqPath
import sbt.internal.Continuous.FileStampRepository
import sbt.internal.util.{ AttributeKey, SourcePosition }
import sbt.internal.{ Clean, Continuous, DynamicInput, SettingsGraph }
import sbt.nio.FileStamp.Formats._
import sbt.nio.FileStamper.{ Hash, LastModified }
import sbt.nio.Keys._
import sbt.nio.file.ChangedFiles
import sbt.std.TaskExtra._
import sjsonnew.JsonFormat

import scala.collection.JavaConverters._
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
      case s =>
        s ::
          injectDependencyMapSettings(s) :::
          maybeAddOutputsAndFileStamps(s, fileOutputScopes, cleanScopes)
    } ++ addCleanImpls(cleanScopes.toSeq)
  }

  private[this] def injectDependencyMapSettings(setting: Def.Setting[_]): List[Def.Setting[_]] = {
    lazy val scope = setting.key.scope in setting.key.key
    val fileMapDependencies = mutable.Set.empty[TaskKey[Map[String, Seq[(Path, FileStamp)]]]]
    val injected = setting.dependencies.toList.flatMap {
      case s if s.key == sbt.Keys.taskScope.key && s.scope.task.toOption.isDefined =>
        val anyScope = s.asInstanceOf[Def.ScopedKey[Scope]]
        val init = Def.pure(() => s.scopedKey.scope)
        Def.setting[Scope](anyScope, init, setting.pos) :: Nil
      case s if s.key == changedInputFiles.key =>
        fileMapDependencies += inputFileDependencyMap in scope
        injectDependencies(scope, s.scope, setting.pos, isInput = true)
      case s if s.key == changedOutputFiles.key =>
        fileMapDependencies += outputFileDependencyMap in scope
        injectDependencies(scope, s.scope, setting.pos, isInput = false)
      case _ => Nil
    }
    if (fileMapDependencies.nonEmpty) {
      val taskSetting = setting.asInstanceOf[Def.Setting[Task[Any]]]
      fileMapDependencies.toList.map { s =>
        addTaskDefinition {
          if (s.key == inputFileDependencyMap.key) s := (inputFileDependencies in scope).value.toMap
          else s := (outputFileDependencies in scope).value.toMap
        }
      } ::: taskSetting.mapInitialize(_.dependsOn(fileMapDependencies.toSeq: _*)) :: injected
    } else injected
  }
  private[this] def injectDependencies(
      scope: Scope,
      changeScope: Scope,
      position: SourcePosition,
      isInput: Boolean
  ): List[Def.Setting[_]] = {
    val injectKey = (if (isInput) inputFileDependencies else outputFileDependencies) in scope
    val stampKey = (if (isInput) inputFileStamps else outputFileStamps) in changeScope
    (injectKey += { changeScope.toString -> stampKey.value }) :: Nil
  }

  /**
   * This method checks if the setting is for a task with a return type in:
   * `File`, `Seq[File]`, `Path`, `Seq[Path`. If it does, then we inject a number of
   * task definition settings that allow the user to check if the output paths of
   * the task have changed. It also adds a custom clean task that will delete the
   * paths returned by the task, provided that they are in the task's target directory. We also inject these tasks if the fileOutputs setting is defined
   * for the task.
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
            val scopedKey = sk.scopedKey.copy(sk.scope in sk.key, Keys.dynamicFileOutputs.key)
            cleanScopes.add(scope)
            addTaskDefinition {
              val init: Def.Initialize[Task[Seq[Path]]] = sk(_.map(_ => Nil))
              Def.setting[Task[Seq[Path]]](scopedKey, init, setting.pos)
            } :: allOutputPathsImpl(scope) :: outputFileStampsImpl(scope) :: cleanImpl(scope) :: Nil
          } else Nil
        }
        ak.manifest.typeArguments match {
          case t :: Nil if seqClass.isAssignableFrom(t.runtimeClass) =>
            t.typeArguments match {
              // Task[Seq[File]]
              case f :: Nil if fileClass.isAssignableFrom(f.runtimeClass) =>
                val sk = setting.asInstanceOf[Def.Setting[Task[Seq[File]]]].key
                val scopedKey = sk.scopedKey.copy(sk.scope in sk.key, Keys.dynamicFileOutputs.key)
                addTaskDefinition {
                  val init: Def.Initialize[Task[Seq[Path]]] = sk(_.map(_.map(_.toPath)))
                  Def.setting[Task[Seq[Path]]](scopedKey, init, setting.pos)
                } :: outputsAndStamps(TaskKey(sk.key) in sk.scope, cleanScopes)
              // Task[Seq[Path]]
              case p :: Nil if pathClass.isAssignableFrom(p.runtimeClass) =>
                val sk = setting.asInstanceOf[Def.Setting[Task[Seq[Path]]]].key
                val scopedKey = sk.scopedKey.copy(sk.scope in sk.key, Keys.dynamicFileOutputs.key)
                addTaskDefinition {
                  val init: Def.Initialize[Task[Seq[Path]]] = sk(_.map(identity))
                  Def.setting[Task[Seq[Path]]](scopedKey, init, setting.pos)
                } :: outputsAndStamps(TaskKey(sk.key) in sk.scope, cleanScopes)
              case _ => default
            }
          // Task[File]
          case t :: Nil if fileClass.isAssignableFrom(t.runtimeClass) =>
            val sk = setting.asInstanceOf[Def.Setting[Task[File]]].key
            val scopedKey = sk.scopedKey.copy(sk.scope in sk.key, Keys.dynamicFileOutputs.key)
            addTaskDefinition {
              val init: Def.Initialize[Task[Seq[Path]]] = sk(_.map(_.toPath :: Nil))
              Def.setting[Task[Seq[Path]]](scopedKey, init, setting.pos)
            } :: outputsAndStamps(TaskKey(sk.key) in sk.scope, cleanScopes)
          // Task[Path]
          case t :: Nil if pathClass.isAssignableFrom(t.runtimeClass) =>
            val sk = setting.asInstanceOf[Def.Setting[Task[Path]]].key
            val scopedKey = sk.scopedKey.copy(sk.scope in sk.key, Keys.dynamicFileOutputs.key)
            addTaskDefinition {
              val init: Def.Initialize[Task[Seq[Path]]] = sk(_.map(_ :: Nil))
              Def.setting[Task[Seq[Path]]](scopedKey, init, setting.pos)
            } :: outputsAndStamps(TaskKey(sk.key) in sk.scope, cleanScopes)
          case _ => default
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
      case changedOutputFiles.key =>
        changedFilesImpl(scopedKey, changedOutputFiles, outputFileStamps)
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
    setting :: (Keys.allInputPathsAndAttributes in scopedKey.scope := {
      val view = (fileTreeView in scopedKey.scope).value
      val inputs = (fileInputs in scopedKey.scope).value
      val stamper = (inputFileStamper in scopedKey.scope).value
      val forceTrigger = (watchForceTriggerOnAnyChange in scopedKey.scope).value
      val dynamicInputs = (Continuous.dynamicInputs in scopedKey.scope).value
      // This makes watch work by ensuring that the input glob is registered with the
      // repository used by the watch process.
      sbt.Keys.state.value.get(globalFileTreeRepository).foreach { repo =>
        inputs.foreach(repo.register)
      }
      dynamicInputs.foreach(_ ++= inputs.map(g => DynamicInput(g, stamper, forceTrigger)))
      view.list(inputs)
    }) :: fileStamps(scopedKey) :: allFilesImpl(scopedKey) :: Nil ++
      changedInputFilesImpl(scopedKey)
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
  private[this] def allFilesImpl(scopedKey: Def.ScopedKey[_]): Def.Setting[_] =
    addTaskDefinition(Keys.allInputFiles in scopedKey.scope := {
      (Keys.allInputPathsAndAttributes in scopedKey.scope).value.collect {
        case (p, a) if a.isRegularFile && !Files.isHidden(p) => p
      }
    })

  /**
   * Returns all of the regular files whose stamp has changed since the last time the
   * task was evaluated. The result includes new and modified files but not deleted
   * files or files whose stamp has not changed since the previous run. Directories and hidden
   * files are excluded
   *
   * @param scopedKey the key whose fileInputs we are seeking
   * @return a task definition that retrieves the changed input files scoped to the key.
   */
  private[this] def changedInputFilesImpl(scopedKey: Def.ScopedKey[_]): Seq[Def.Setting[_]] =
    changedFilesImpl(scopedKey, changedInputFiles, inputFileStamps) ::
      (watchForceTriggerOnAnyChange in scopedKey.scope := {
        (watchForceTriggerOnAnyChange in scopedKey.scope).?.value match {
          case Some(t) => t
          case None    => false
        }
      }) :: Nil
  private[this] def changedFilesImpl(
      scopedKey: Def.ScopedKey[_],
      changeKey: TaskKey[Seq[(Path, FileStamp)] => Option[ChangedFiles]],
      stampKey: TaskKey[Seq[(Path, FileStamp)]]
  ): Def.Setting[_] =
    addTaskDefinition(changeKey in scopedKey.scope := {
      FileChanges.apply((stampKey in scopedKey.scope).value, _)
    })

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
  private[sbt] def fileStamps(scopedKey: Def.ScopedKey[_]): Def.Setting[_] =
    addTaskDefinition(Keys.inputFileStamps in scopedKey.scope := {
      val cache = (unmanagedFileStampCache in scopedKey.scope).value
      val stamper = (Keys.inputFileStamper in scopedKey.scope).value
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
      (Keys.allInputPathsAndAttributes in scopedKey.scope).value.flatMap {
        case (path, a) if a.isRegularFile && !Files.isHidden(path) => stampFile(path)
        case _                                                     => None
      }
    })
  private[this] def outputsAndStamps[T: JsonFormat: ToSeqPath](
      taskKey: TaskKey[T],
      cleanScopes: mutable.Set[Scope]
  ): List[Def.Setting[_]] = {
    val scope = taskKey.scope in taskKey.key
    cleanScopes.add(scope)
    allOutputPathsImpl(scope) :: outputFileStampsImpl(scope) :: cleanImpl(taskKey) :: Nil
  }
  private[this] def allOutputPathsImpl(scope: Scope): Def.Setting[_] =
    addTaskDefinition(allOutputFiles in scope := {
      val fileOutputGlobs = (fileOutputs in scope).value
      val allFileOutputs = fileTreeView.value.list(fileOutputGlobs).map(_._1)
      val dynamicOutputs = (dynamicFileOutputs in scope).value
      allFileOutputs ++ dynamicOutputs.filterNot(p => fileOutputGlobs.exists(_.matches(p)))
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
