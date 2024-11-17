/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.{ Load, BuildStructure, Act, Aggregation, SessionSettings }
import Scope.GlobalScope
import Def.{ ScopedKey, Setting }
import sbt.internal.util.complete.Parser
import sbt.util.Show
import std.Transform.DummyTaskMap
import sbt.EvaluateTask.extractedTaskConfig
import sbt.ProjectExtra.setProject
import sbt.SlashSyntax0.given

final case class Extracted(
    structure: BuildStructure,
    session: SessionSettings,
    currentRef: ProjectRef
)(implicit val showKey: Show[ScopedKey[?]]) {
  def rootProject = structure.rootProject
  lazy val currentUnit = structure units currentRef.build
  lazy val currentProject = currentUnit defined currentRef.project
  lazy val currentLoader: ClassLoader = currentUnit.loader

  /**
   * Gets the value assigned to `key` in the computed settings map.
   * If the project axis is not explicitly specified, it is resolved to be the current project according to the extracted `session`.
   * Other axes are resolved to be `Zero` if they are not specified.
   */
  def get[T](key: SettingKey[T]): T = getOrError(inCurrent(key.scopedKey))
  def get[T](key: TaskKey[T]): Task[T] = getOrError(inCurrent(key.scopedKey))

  /**
   * Gets the value assigned to `key` in the computed settings map wrapped in Some.  If it does not exist, None is returned.
   * If the project axis is not explicitly specified, it is resolved to be the current project according to the extracted `session`.
   * Other axes are resolved to be `Zero` if they are not specified.
   */
  def getOpt[T](key: SettingKey[T]): Option[T] = structure.data.get(inCurrent(key.scopedKey))
  def getOpt[T](key: TaskKey[T]): Option[Task[T]] =
    structure.data.get(inCurrent(key))

  private def inCurrent[T](key: ScopedKey[T]): ScopedKey[T] =
    if key.scope.project == This then key.copy(scope = key.scope.rescope(currentRef))
    else key

  /**
   * Runs the task specified by `key` and returns the transformed State and the resulting value of the task.
   * If the project axis is not defined for the key, it is resolved to be the current project.
   * Other axes are resolved to `Zero` if unspecified.
   *
   * This method requests execution of only the given task and does not aggregate execution.
   * See `runAggregated` for that.
   */
  def runTask[T](key: TaskKey[T], state: State): (State, T) = {
    val rkey = resolve(key)
    val config = extractedTaskConfig(this, structure, state)
    val value: Option[(State, Result[T])] =
      EvaluateTask(structure, key.scopedKey, state, currentRef, config)
    val (newS, result) = getOrError(rkey.scopedKey, value)
    (newS, EvaluateTask.processResult2(result))
  }

  /**
   * Runs the input task specified by `key`, using the `input` as the input to it, and returns the transformed State
   * and the resulting value of the input task.
   *
   * If the project axis is not defined for the key, it is resolved to be the current project.
   * Other axes are resolved to `Zero` if unspecified.
   *
   * This method requests execution of only the given task and does not aggregate execution.
   */
  def runInputTask[T](key: InputKey[T], input: String, state: State): (State, T) = {
    val key2 = Scoped.scopedSetting(
      Scope.resolveScope(Load.projectScope(currentRef), currentRef.build, rootProject)(key.scope),
      key.key
    )
    val rkey = resolve(key2)
    val inputTask = get(rkey)
    val task = Parser.parse(input, inputTask.parser(state)) match {
      case Right(t)  => t
      case Left(msg) => sys.error(s"Invalid programmatic input:\n$msg")
    }
    val config = extractedTaskConfig(this, structure, state)
    EvaluateTask.withStreams(structure, state) { str =>
      val nv = EvaluateTask.nodeView(state, str, rkey.scopedKey :: Nil)
      val (newS, result) =
        EvaluateTask.runTask(task, state, str, structure.index.triggers, config)(using nv)
      (newS, EvaluateTask.processResult2(result))
    }
  }

  /**
   * Runs the tasks selected by aggregating `key` and returns the transformed State.
   * If the project axis is not defined for the key, it is resolved to be the current project.
   * The project axis is what determines where aggregation starts, so ensure this is set to what you want.
   * Other axes are resolved to `Zero` if unspecified.
   */
  def runAggregated[A1](key: TaskKey[A1], state: State): State =
    val rkey = resolve(key)
    val keys = Aggregation.aggregate(rkey, ScopeMask(), structure.extra)
    val tasks = Act.keyValues(structure)(keys)
    Aggregation.runTasks(
      state,
      tasks,
      DummyTaskMap(Nil),
      show = Aggregation.defaultShow(state, false),
    )

  private def resolve[K <: Scoped.ScopingSetting[K] & Scoped](key: K): K =
    Scope.resolveScope(GlobalScope, currentRef.build, rootProject)(key.scope) / key

  private def getOrError[T](key: ScopedKey[?], value: Option[T])(using
      display: Show[ScopedKey[?]]
  ): T =
    value.getOrElse(sys.error(display.show(key) + " is undefined."))

  private def getOrError[T](key: ScopedKey[T])(using
      display: Show[ScopedKey[?]]
  ): T =
    getOrError(key, structure.data.get(key))(using display)

  @deprecated(
    "This discards session settings. Migrate to appendWithSession or appendWithoutSession.",
    "1.2.0"
  )
  def append(settings: Seq[Setting[?]], state: State): State =
    appendWithoutSession(settings, state)

  /** Appends the given settings to all the build state settings, including session settings. */
  def appendWithSession(settings: Seq[Setting[?]], state: State): State =
    appendImpl(settings, state, session.mergeSettings)

  /**
   * Appends the given settings to the original build state settings, discarding any settings
   * appended to the session in the process.
   */
  def appendWithoutSession(settings: Seq[Setting[?]], state: State): State =
    appendImpl(settings, state, session.original)

  private def appendImpl(
      settings: Seq[Setting[?]],
      state: State,
      sessionSettings: Seq[Setting[?]],
  ): State = {
    val appendSettings =
      Load.transformSettings(Load.projectScope(currentRef), currentRef.build, rootProject, settings)
    val newStructure = Load.reapply(sessionSettings ++ appendSettings, structure)
    Project.setProject(session, newStructure, state)
  }
}
