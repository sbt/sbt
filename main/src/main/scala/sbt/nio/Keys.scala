/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.nio

import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

import sbt.Def.{ settingKey, taskKey }
import sbt.KeyRanks.{ BMinusSetting, DSetting, Invisible }
import sbt.internal.DynamicInput
import sbt.internal.nio.FileTreeRepository
import sbt.internal.util.AttributeKey
import sbt.internal.util.complete.Parser
import sbt.nio.file.{ FileAttributes, FileTreeView, Glob, PathFilter }
import sbt.*

import scala.concurrent.duration.FiniteDuration

object Keys {
  sealed trait WatchBuildSourceOption
  case object IgnoreSourceChanges extends WatchBuildSourceOption
  case object WarnOnSourceChanges extends WatchBuildSourceOption
  case object ReloadOnSourceChanges extends WatchBuildSourceOption
  val allInputFiles =
    taskKey[Seq[Path]]("All of the file inputs for a task excluding directories and hidden files.")
  val changedInputFiles =
    taskKey[Seq[(Path, FileStamp)] => FileChanges]("The changed files for a task")
  val fileInputs = settingKey[Seq[Glob]](
    "The file globs that are used by a task. This setting will generally be scoped per task. It will also be used to determine the sources to watch during continuous execution."
  )
  val fileInputIncludeFilter =
    settingKey[PathFilter]("A filter to apply to the input sources of a task.")
  val fileInputExcludeFilter =
    settingKey[PathFilter]("An exclusion filter to apply to the input sources of a task.")
  val inputFileStamper = settingKey[FileStamper](
    "Toggles the file stamping implementation used to determine whether or not a file has been modified."
  )

  val fileOutputs = settingKey[Seq[Glob]]("Describes the output files of a task.")
  val fileOutputIncludeFilter =
    settingKey[PathFilter]("A filter to apply to the outputs of a task.")
  val fileOutputExcludeFilter =
    settingKey[PathFilter]("An exclusion filter to apply to the outputs of a task.")
  val allOutputFiles =
    taskKey[Seq[Path]]("All of the file outputs for a task excluding directories and hidden files.")
  val changedOutputFiles =
    taskKey[Seq[(Path, FileStamp)] => FileChanges](
      "The files that have changed since the last task run."
    )
  val outputFileStamper = settingKey[FileStamper](
    "Toggles the file stamping implementation used to determine whether or not a file has been modified."
  )

  val fileTreeView =
    taskKey[FileTreeView.Nio[FileAttributes]]("A view of the local file system tree")

  val checkBuildSources =
    taskKey[StateTransform]("Check if any meta build sources have changed").withRank(DSetting)

  // watch related settings
  val watchAntiEntropyRetentionPeriod = settingKey[FiniteDuration](
    "Wall clock Duration for which a FileEventMonitor will store anti-entropy events. This prevents spurious triggers when a task takes a long time to run. Higher values will consume more memory but make spurious triggers less likely."
  ).withRank(BMinusSetting)
  val watchAntiEntropyPollPeriod = settingKey[FiniteDuration](
    "The duration for which sbt will poll for file events during the window in which sbt is buffering file events"
  )
  val onChangedBuildSource = settingKey[WatchBuildSourceOption](
    "Determines what to do if the sbt meta build sources have changed"
  ).withRank(DSetting)

  val watchDeletionQuarantinePeriod = settingKey[FiniteDuration](
    "Period for which deletion events will be quarantined. This is to prevent spurious builds when a file is updated with a rename which manifests as a file deletion followed by a file creation. The higher this value is set, the longer the delay will be between a file deletion and a build trigger but the less likely it is for a spurious trigger."
  ).withRank(DSetting)
  private val forceTriggerOnAnyChangeMessage =
    "Force the watch process to rerun the current task(s) if any relevant source change is " +
      "detected regardless of whether or not the underlying file has actually changed."

  // watch related keys
  val watchBeforeCommand = settingKey[() => Unit](
    "Function to run prior to running a command in a continuous build."
  ).withRank(DSetting)
  val watchForceTriggerOnAnyChange =
    Def.settingKey[Boolean](forceTriggerOnAnyChangeMessage).withRank(DSetting)
  private[sbt] val watchInputHandler = settingKey[InputStream => Watch.Action](
    "Function that is periodically invoked to determine if the continuous build should be stopped or if a build should be triggered. It will usually read from stdin to respond to user commands. This is only invoked if watchInputStream is set."
  ).withRank(DSetting)
  val watchInputOptionsMessage = settingKey[String]("The help message for the watch input options")
  val watchInputOptions = settingKey[Seq[Watch.InputOption]]("The available input options")
  val watchInputStream = taskKey[InputStream](
    "The input stream to read for user input events. This will usually be System.in"
  ).withRank(DSetting)
  val watchInputParser = settingKey[Parser[Watch.Action]](
    "A parser of user input that can be used to trigger or exit a continuous build"
  ).withRank(DSetting)
  val watchLogLevel =
    settingKey[sbt.util.Level.Value]("Transform the default logger in continuous builds.")
      .withRank(DSetting)
  val watchOnFileInputEvent = settingKey[(Int, Watch.Event) => Watch.Action](
    "Callback to invoke if an event is triggered in a continuous build by one of the files matching an fileInput glob for the task and its transitive dependencies"
  ).withRank(DSetting)
  val watchOnIteration = settingKey[(Int, ProjectRef, Seq[String]) => Watch.Action](
    "Function that is invoked before waiting for file system events or user input events."
  ).withRank(DSetting)
  val watchOnTermination = settingKey[(Watch.Action, String, Int, State) => State](
    "Transforms the state upon completion of a watch. The String argument is the command that was run during the watch. The Int parameter specifies how many times the command was run during the watch."
  ).withRank(DSetting)
  val watchPersistFileStamps = settingKey[Boolean](
    "Toggles whether or not the continuous build will reuse the file stamps computed in previous runs. Setting this to true decrease watch startup latency but could cause inconsistent results if many source files are concurrently modified."
  ).withRank(DSetting)
  val watchStartMessage = settingKey[(Int, ProjectRef, Seq[String]) => Option[String]](
    "The message to show when triggered execution waits for sources to change. The parameters are the current watch iteration count, the current project name and the tasks that are being run with each build."
  ).withRank(DSetting)
  // The watchTasks key should really be named watch, but that is already taken by the deprecated watch key. I'd be surprised if there are any plugins that use it so I think we should consider breaking binary compatibility to rename this task.
  @deprecated("The watch input task no longer has any effect.", "1.4.0")
  val watchTasks = InputKey[StateTransform](
    "watch",
    "Watch a task (or multiple tasks) and rebuild when its file inputs change or user input is received. The semantics are more or less the same as the `~` command except that it cannot transform the state on exit. This means that it cannot be used to reload the build."
  ).withRank(DSetting)
  val watchTriggers =
    settingKey[Seq[Glob]]("Describes files that should trigger a new continuous build.")

  /**
   * Sets the message to display after a new build is triggered. By default,
   * it prints the file that triggered the build and what command(s) will be run.
   * To clear the screen, add
   * {{{
   *   watchTriggeredMessage := Watch.clearScreenOnTrigger
   * }}}
   * to the build.
   */
  val watchTriggeredMessage = settingKey[(Int, Path, Seq[String]) => Option[String]](
    "The message to show before triggered execution executes an action after sources change. The parameters are the current watch iteration count, the path that triggered the build and the names of the commands to run."
  ).withRank(DSetting)

  // internal keys
  private[sbt] val globalFileTreeRepository = AttributeKey[FileTreeRepository[FileAttributes]](
    "global-file-tree-repository",
    "Provides a view into the file system that may or may not cache the tree in memory",
    Int.MaxValue
  )
  private[sbt] val dynamicDependency = settingKey[Unit](
    "Leaves a breadcrumb that the scoped task is evaluated inside of a dynamic task"
  ).withRank(Invisible)
  private[sbt] val transitiveClasspathDependency = settingKey[Unit](
    "Leaves a breadcrumb that the scoped task has transitive classpath dependencies"
  ).withRank(Invisible)
  private[sbt] val transitiveDynamicInputs =
    taskKey[Seq[DynamicInput]]("The transitive inputs and triggers for a key").withRank(Invisible)
  private[sbt] val dynamicFileOutputs =
    taskKey[Seq[Path]]("The outputs of a task").withRank(Invisible)

  val inputFileStamps =
    taskKey[Seq[(Path, FileStamp)]]("Retrieves the hashes for a set of task input files")
      .withRank(Invisible)
  val outputFileStamps =
    taskKey[Seq[(Path, FileStamp)]]("Retrieves the hashes for a set of task output files")
      .withRank(Invisible)
  private[sbt] type FileAttributeMap =
    java.util.Map[Path, FileStamp]
  private[sbt] val persistentFileStampCache =
    AttributeKey[FileStamp.Cache]("persistent-file-stamp-cache", Int.MaxValue)
  private[sbt] val allInputPathsAndAttributes =
    taskKey[Seq[(Path, FileAttributes)]]("Get all of the file inputs for a task")
      .withRank(Invisible)
  private[sbt] val unmanagedFileStampCache = taskKey[FileStamp.Cache](
    "Map of managed file stamps that may be cleared between task evaluation runs."
  ).withRank(Invisible)
  private[sbt] val managedFileStampCache = taskKey[FileStamp.Cache](
    "Map of managed file stamps that may be cleared between task evaluation runs."
  ).withRank(Invisible)
  private[sbt] val managedSourcePaths =
    taskKey[Seq[Path]]("Transforms the managedSources to Seq[Path] to induce setting injection.")
      .withRank(Invisible)
  private[sbt] val dependencyClasspathFiles =
    taskKey[Seq[Path]]("The dependency classpath for a task.").withRank(Invisible)
  private[sbt] val compileOutputs = taskKey[Seq[Path]]("Compilation outputs").withRank(Invisible)

  private val hasCheckedMetaBuildMsg =
    "Indicates whether or not we have called the checkBuildSources task. This is to avoid warning " +
      "user about build source changes if the build sources were changed while sbt was shutdown. " +
      " When that occurs, the previous cache reflects the state of the old build files, but by " +
      " the time the checkBuildSources task has run, the build will have already been loaded with the " +
      " new meta build sources so we should neither warn the user nor automatically restart the build"
  private[sbt] val hasCheckedMetaBuild =
    AttributeKey[AtomicBoolean]("has-checked-meta-build", hasCheckedMetaBuildMsg, Int.MaxValue)
}
