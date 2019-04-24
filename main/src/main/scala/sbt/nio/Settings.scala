/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package nio

import java.nio.file.{ Files, Path }

import sbt.Keys._
import sbt.internal.{ Continuous, DynamicInput }
import sbt.nio.FileStamp.{ fileStampJsonFormatter, pathJsonFormatter }
import sbt.nio.FileStamper.{ Hash, LastModified }
import sbt.nio.Keys._

private[sbt] object Settings {

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
   * @param scopedKey the key whose fileInputs we are seeking
   * @return a task definition that retrieves the file input files and their attributes scoped to a particular task.
   */
  private[sbt] def allPathsAndAttributes(scopedKey: Def.ScopedKey[_]): Def.Setting[_] =
    Keys.allPathsAndAttributes in scopedKey.scope := {
      val view = (fileTreeView in scopedKey.scope).value
      val inputs = (fileInputs in scopedKey.scope).value
      val stamper = (fileStamper in scopedKey.scope).value
      val forceTrigger = (watchForceTriggerOnAnyChange in scopedKey.scope).value
      val dynamicInputs = Continuous.dynamicInputs.value
      sbt.Keys.state.value.get(globalFileTreeRepository).foreach { repo =>
        inputs.foreach(repo.register)
      }
      dynamicInputs.foreach(_ ++= inputs.map(g => DynamicInput(g, stamper, forceTrigger)))
      view.list(inputs)
    }

  /**
   * Returns all of the paths described by a glob with no additional filtering.
   * No additional filtering is performed.
   *
   * @param scopedKey the key whose file inputs we are seeking
   * @return a task definition that retrieves the input files and their attributes scoped to a particular task.
   */
  private[sbt] def allPaths(scopedKey: Def.ScopedKey[_]): Def.Setting[_] =
    addTaskDefinition(Keys.allPaths in scopedKey.scope := {
      (Keys.allPathsAndAttributes in scopedKey.scope).value.map(_._1)
    })

  /**
   * Returns all of the paths for the regular files described by a glob. Directories and hidden
   * files are excluded.
   *
   * @param scopedKey the key whose file inputs we are seeking
   * @return a task definition that retrieves all of the input paths scoped to the input key.
   */
  private[sbt] def allFiles(scopedKey: Def.ScopedKey[_]): Def.Setting[_] =
    addTaskDefinition(Keys.allFiles in scopedKey.scope := {
      (Keys.allPathsAndAttributes in scopedKey.scope).value.collect {
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
  private[sbt] def changedFiles(scopedKey: Def.ScopedKey[_]): Seq[Def.Setting[_]] =
    addTaskDefinition(Keys.changedFiles in scopedKey.scope := {
      val current = (Keys.fileStamps in scopedKey.scope).value
      (Keys.fileStamps in scopedKey.scope).previous match {
        case Some(previous) => (current diff previous).map(_._1)
        case None           => current.map(_._1)
      }
    }) :: (watchForceTriggerOnAnyChange in scopedKey.scope := {
      (watchForceTriggerOnAnyChange in scopedKey.scope).?.value match {
        case Some(t) => t
        case None    => false
      }
    }) :: Nil

  /**
   * Returns all of the regular files and the corresponding file stamps for the file inputs
   * scoped to the input key. Directories and hidden files are excluded.
   *
   * @param scopedKey the key whose fileInputs we are seeking
   * @return a task definition that retrieves the input files and their file stamps scoped to the
   *         input key.
   */
  private[sbt] def fileStamps(scopedKey: Def.ScopedKey[_]): Def.Setting[_] =
    addTaskDefinition(Keys.fileStamps in scopedKey.scope := {
      val stamper = (Keys.stamper in scopedKey.scope).value
      (Keys.allPathsAndAttributes in scopedKey.scope).value.collect {
        case (p, a) if a.isRegularFile && !Files.isHidden(p) => p -> stamper(p)
      }
    })

  /**
   * Returns all of the regular files whose stamp has changed since the last time the
   * task was evaluated. The result includes modified files but neither new nor deleted
   * files nor files whose stamp has not changed since the previous run. Directories and
   * hidden files are excluded.
   *
   * @param scopedKey the key whose modified files we are seeking
   * @return a task definition that retrieves the changed input files scoped to the key.
   */
  private[sbt] def modifiedFiles(scopedKey: Def.ScopedKey[_]): Seq[Def.Setting[_]] =
    (Keys.modifiedFiles in scopedKey.scope := {
      val current = (Keys.fileStamps in scopedKey.scope).value
      (Keys.fileStamps in scopedKey.scope).previous match {
        case Some(previous) =>
          val previousPathSet = previous.view.map(_._1).toSet
          (current diff previous).collect { case (p, a) if previousPathSet(p) => p }
        case None => current.map(_._1)
      }
    }).mapInit((sk, task) => Task(task.info.set(sbt.Keys.taskDefinitionKey, sk), task.work)) ::
      (watchForceTriggerOnAnyChange in scopedKey.scope := {
        (watchForceTriggerOnAnyChange in scopedKey.scope).?.value match {
          case Some(t) => t
          case None    => false
        }
      }) :: Nil

  /**
   * Returns all of the files that have been removed since the previous run.
   * task was evaluated. The result includes modified files but neither new nor deleted
   * files nor files whose stamp has not changed since the previous run. Directories and
   * hidden files are excluded
   *
   * @param scopedKey the key whose removed files we are seeking
   * @return a task definition that retrieves the changed input files scoped to the key.
   */
  private[sbt] def removedFiles(scopedKey: Def.ScopedKey[_]): Def.Setting[_] =
    addTaskDefinition(Keys.removedFiles in scopedKey.scope := {
      val current = (Keys.allFiles in scopedKey.scope).value
      (Keys.allFiles in scopedKey.scope).previous match {
        case Some(previous) => previous diff current
        case None           => Nil
      }
    }).mapInit((sk, task) => Task(task.info.set(sbt.Keys.taskDefinitionKey, sk), task.work))

  /**
   * Returns a function from `Path` to [[FileStamp]] that can be used by tasks to retrieve
   * the stamp for a file. It has the side effect of stamping the file if it has not already
   * been stamped during the task evaluation.
   *
   * @return a task definition for a function from `Path` to [[FileStamp]].
   */
  private[sbt] def stamper(scopedKey: Def.ScopedKey[_]): Def.Setting[_] =
    addTaskDefinition((Keys.stamper in scopedKey.scope) := {
      val attributeMap = Keys.fileAttributeMap.value
      val stamper = (Keys.fileStamper in scopedKey.scope).value
      path: Path =>
        attributeMap.get(path) match {
          case null =>
            val stamp = stamper match {
              case Hash         => FileStamp.hash(path)
              case LastModified => FileStamp.lastModified(path)
            }
            attributeMap.put(path, stamp)
            stamp
          case s => s
        }
    })
}
