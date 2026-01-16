/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sjsonnew.JsonFormat
import Def.Setting
import sbt.internal.{ BuildStructure, LoadedBuildUnit, SessionSettings }
import sbt.ProjectExtra.*

/**
 * Extends State with setting-level knowledge.
 */
trait UpperStateOps extends Any {

  /**
   * ProjectRef to the current project of the state session that can be change using
   * `project` command.
   */
  def currentRef: ProjectRef

  /**
   * Current project of the state session that can be change using `project` command.
   */
  def currentProject: ResolvedProject

  private[sbt] def structure: BuildStructure

  private[sbt] def session: SessionSettings

  private[sbt] def currentUnit: LoadedBuildUnit

  /**
   * Gets the value assigned to `key` in the computed settings map.
   * If the project axis is not explicitly specified, it is resolved to be the current project according to the extracted `session`.
   * Other axes are resolved to be `Zero` if they are not specified.
   */
  def getSetting[A](key: SettingKey[A]): Option[A]

  /**
   * Gets the value assigned to `key` in the computed settings map.
   * If the project axis is not explicitly specified, it is resolved to be the current project according to the extracted `session`.
   * Other axes are resolved to be `Zero` if they are not specified.
   */
  def getTaskValue[A](key: TaskKey[A]): Option[Task[A]]

  /**
   * Gets the value assigned to `key` in the computed settings map.
   * If the project axis is not explicitly specified, it is resolved to be the current project according to the extracted `session`.
   * Other axes are resolved to be `Zero` if they are not specified.
   */
  def setting[A](key: SettingKey[A]): A

  /**
   * Gets the value assigned to `key` in the computed settings map.
   * If the project axis is not explicitly specified, it is resolved to be the current project according to the extracted `session`.
   * Other axes are resolved to be `Zero` if they are not specified.
   */
  def taskValue[A](key: TaskKey[A]): Task[A]

  /**
   * Runs the task specified by `key` and returns the transformed State and the resulting value of the task.
   * If the project axis is not defined for the key, it is resolved to be the current project.
   * Other axes are resolved to `Zero` if unspecified.
   *
   * This method requests execution of only the given task and does not aggregate execution.
   * See `runAggregated` for that.
   *
   * To avoid race conditions, this should NOT be called from a task.
   */
  def unsafeRunTask[A](key: TaskKey[A]): (State, A)

  /**
   * Runs the input task specified by `key`, using the `input` as the input to it, and returns the transformed State
   * and the resulting value of the input task.
   *
   * If the project axis is not defined for the key, it is resolved to be the current project.
   * Other axes are resolved to `Zero` if unspecified.
   *
   * This method requests execution of only the given task and does not aggregate execution.
   * To avoid race conditions, this should NOT be called from a task.
   */
  def unsafeRunInputTask[A](key: InputKey[A], input: String): (State, A)

  /**
   * Runs the tasks selected by aggregating `key` and returns the transformed State.
   * If the project axis is not defined for the key, it is resolved to be the current project.
   * The project axis is what determines where aggregation starts, so ensure this is set to what you want.
   * Other axes are resolved to `Zero` if unspecified.
   *
   * To avoid race conditions, this should NOT be called from a task.
   */
  def unsafeRunAggregated[A](key: TaskKey[A]): State

  /** Appends the given settings to all the build state settings, including session settings. */
  def appendWithSession(settings: Seq[Setting[?]]): State

  /**
   * Appends the given settings to the original build state settings, discarding any settings
   * appended to the session in the process.
   */
  def appendWithoutSession(settings: Seq[Setting[?]], state: State): State

  def respondEvent[A: JsonFormat](event: A): Unit
  def respondError(code: Long, message: String): Unit
  def notifyEvent[A: JsonFormat](method: String, params: A): Unit
}

object UpperStateOps {
  lazy val exchange = StandardMain.exchange

  implicit class UpperStateOpsImpl(val s: State) extends AnyVal with UpperStateOps {
    def extract: Extracted = Project.extract(s)

    def currentRef = extract.currentRef

    def currentProject: ResolvedProject = extract.currentProject

    private[sbt] def structure: BuildStructure = Project.structure(s)

    private[sbt] def currentUnit: LoadedBuildUnit = extract.currentUnit

    private[sbt] def session: SessionSettings = Project.session(s)

    def getSetting[A](key: SettingKey[A]): Option[A] = extract.getOpt(key)

    def getTaskValue[A](key: TaskKey[A]): Option[Task[A]] = extract.getOpt(key)

    def setting[A](key: SettingKey[A]): A = extract.get(key)

    def taskValue[A](key: TaskKey[A]): Task[A] = extract.get(key)

    def unsafeRunTask[A](key: TaskKey[A]): (State, A) = extract.runTask(key, s)

    def unsafeRunInputTask[A](key: InputKey[A], input: String): (State, A) =
      extract.runInputTask(key, input, s)

    def unsafeRunAggregated[A](key: TaskKey[A]): State =
      extract.runAggregated(key, s)

    def appendWithSession(settings: Seq[Setting[?]]): State =
      extract.appendWithSession(settings, s)

    def appendWithoutSession(settings: Seq[Setting[?]], state: State): State =
      extract.appendWithoutSession(settings, s)

    def respondEvent[A: JsonFormat](event: A): Unit = {
      exchange.respondEvent(event, s.currentCommand.flatMap(_.execId), s.source)
    }
    def respondError(code: Long, message: String): Unit = {
      exchange.respondError(code, message, s.currentCommand.flatMap(_.execId), s.source)
    }
    def notifyEvent[A: JsonFormat](method: String, params: A): Unit = {
      exchange.notifyEvent(method, params)
    }
  }
}
