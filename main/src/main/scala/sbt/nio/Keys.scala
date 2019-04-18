/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.nio

import java.io.InputStream
import java.nio.file.Path

import sbt.BuildSyntax.{ settingKey, taskKey }
import sbt.KeyRanks.{ BMinusSetting, DSetting }
import sbt.internal.DynamicInput
import sbt.internal.nio.FileTreeRepository
import sbt.internal.util.AttributeKey
import sbt.internal.util.complete.Parser
import sbt.nio.file.{ FileAttributes, FileTreeView, Glob }
import sbt.{ Def, InputKey, State, StateTransform }

import scala.concurrent.duration.FiniteDuration

object Keys {
  val allPaths = taskKey[Seq[Path]](
    "All of the file inputs for a task with no filters applied. Regular files and directories are included."
  )
  val changedFiles =
    taskKey[Seq[Path]](
      "All of the file inputs for a task that have changed since the last run. Includes new and modified files but excludes deleted files."
    )
  val modifiedFiles =
    taskKey[Seq[Path]](
      "All of the file inputs for a task that have changed since the last run. Files are considered modified based on either the last modified time or the file stamp for the file."
    )
  val removedFiles =
    taskKey[Seq[Path]]("All of the file inputs for a task that have changed since the last run.")
  val allFiles =
    taskKey[Seq[Path]]("All of the file inputs for a task excluding directories and hidden files.")
  val fileInputs = settingKey[Seq[Glob]](
    "The file globs that are used by a task. This setting will generally be scoped per task. It will also be used to determine the sources to watch during continuous execution."
  )
  val fileOutputs = taskKey[Seq[Glob]]("Describes the output files of a task.")
  val fileStamper = settingKey[FileStamper](
    "Toggles the file stamping implementation used to determine whether or not a file has been modified."
  )
  val fileTreeView =
    taskKey[FileTreeView.Nio[FileAttributes]]("A view of the local file system tree")
  private[sbt] val fileStamps =
    taskKey[Seq[(Path, FileStamp)]]("Retrieves the hashes for a set of files")
  private[sbt] type FileAttributeMap =
    java.util.HashMap[Path, FileStamp]
  private[sbt] val persistentFileAttributeMap =
    AttributeKey[FileAttributeMap]("persistent-file-attribute-map", Int.MaxValue)
  private[sbt] val allPathsAndAttributes =
    taskKey[Seq[(Path, FileAttributes)]]("Get all of the file inputs for a task")
  private[sbt] val fileAttributeMap = taskKey[FileAttributeMap](
    "Map of file stamps that may be cleared between task evaluation runs."
  )
  private[sbt] val pathToFileStamp = taskKey[Path => FileStamp](
    "A function that computes a file stamp for a path. It may have the side effect of updating a cache."
  )

  val watchAntiEntropyRetentionPeriod = settingKey[FiniteDuration](
    "Wall clock Duration for which a FileEventMonitor will store anti-entropy events. This prevents spurious triggers when a task takes a long time to run. Higher values will consume more memory but make spurious triggers less likely."
  ).withRank(BMinusSetting)
  val watchDeletionQuarantinePeriod = settingKey[FiniteDuration](
    "Period for which deletion events will be quarantined. This is to prevent spurious builds when a file is updated with a rename which manifests as a file deletion followed by a file creation. The higher this value is set, the longer the delay will be between a file deletion and a build trigger but the less likely it is for a spurious trigger."
  ).withRank(DSetting)
  private[this] val forceTriggerOnAnyChangeMessage =
    "Force the watch process to rerun the current task(s) if any relevant source change is " +
      "detected regardless of whether or not the underlying file has actually changed."

  val watchForceTriggerOnAnyChange =
    Def.settingKey[Boolean](forceTriggerOnAnyChangeMessage).withRank(DSetting)
  val watchLogLevel =
    settingKey[sbt.util.Level.Value]("Transform the default logger in continuous builds.")
      .withRank(DSetting)
  val watchInputHandler = settingKey[InputStream => Watch.Action](
    "Function that is periodically invoked to determine if the continuous build should be stopped or if a build should be triggered. It will usually read from stdin to respond to user commands. This is only invoked if watchInputStream is set."
  ).withRank(DSetting)
  val watchInputStream = taskKey[InputStream](
    "The input stream to read for user input events. This will usually be System.in"
  ).withRank(DSetting)
  val watchInputParser = settingKey[Parser[Watch.Action]](
    "A parser of user input that can be used to trigger or exit a continuous build"
  ).withRank(DSetting)
  val watchOnEnter = settingKey[() => Unit](
    "Function to run prior to beginning a continuous build. This will run before the continuous task(s) is(are) first evaluated."
  ).withRank(DSetting)
  val watchOnExit = settingKey[() => Unit](
    "Function to run upon exit of a continuous build. It can be used to cleanup resources used during the watch."
  ).withRank(DSetting)
  val watchOnFileInputEvent = settingKey[(Int, Watch.Event) => Watch.Action](
    "Callback to invoke if an event is triggered in a continuous build by one of the files matching an fileInput glob for the task and its transitive dependencies"
  ).withRank(DSetting)
  val watchOnIteration = settingKey[Int => Watch.Action](
    "Function that is invoked before waiting for file system events or user input events."
  ).withRank(DSetting)
  val watchOnTermination = settingKey[(Watch.Action, String, Int, State) => State](
    "Transforms the state upon completion of a watch. The String argument is the command that was run during the watch. The Int parameter specifies how many times the command was run during the watch."
  ).withRank(DSetting)
  val watchStartMessage = settingKey[(Int, String, Seq[String]) => Option[String]](
    "The message to show when triggered execution waits for sources to change. The parameters are the current watch iteration count, the current project name and the tasks that are being run with each build."
  ).withRank(DSetting)
  // The watchTasks key should really be named watch, but that is already taken by the deprecated watch key. I'd be surprised if there are any plugins that use it so I think we should consider breaking binary compatibility to rename this task.
  val watchTasks = InputKey[StateTransform](
    "watch",
    "Watch a task (or multiple tasks) and rebuild when its file inputs change or user input is received. The semantics are more or less the same as the `~` command except that it cannot transform the state on exit. This means that it cannot be used to reload the build."
  ).withRank(DSetting)
  val watchTrackMetaBuild = settingKey[Boolean](
    "Toggles whether or not changing the build files (e.g. **/*.sbt, project/**/(*.scala | *.java)) should automatically trigger a project reload"
  ).withRank(DSetting)
  val watchTriggeredMessage = settingKey[(Int, Path, Seq[String]) => Option[String]](
    "The message to show before triggered execution executes an action after sources change. The parameters are the path that triggered the build and the current watch iteration count."
  ).withRank(DSetting)

  private[sbt] val globalFileTreeRepository = AttributeKey[FileTreeRepository[FileAttributes]](
    "global-file-tree-repository",
    "Provides a view into the file system that may or may not cache the tree in memory",
    1000
  )
  private[sbt] val dynamicDependency = settingKey[Unit](
    "Leaves a breadcrumb that the scoped task is evaluated inside of a dynamic task"
  )
  private[sbt] val transitiveClasspathDependency = settingKey[Unit](
    "Leaves a breadcrumb that the scoped task has transitive classpath dependencies"
  )
  private[sbt] val transitiveDynamicInputs =
    taskKey[Seq[DynamicInput]]("The transitive inputs and triggers for a key")
}
