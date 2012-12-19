package sbt

	import Project._
	import Scope.GlobalScope
	import Def.{ScopedKey, Setting}
	import std.Transform.DummyTaskMap

final case class Extracted(structure: BuildStructure, session: SessionSettings, currentRef: ProjectRef)(implicit val showKey: Show[ScopedKey[_]])
{
	def rootProject = structure.rootProject
	lazy val currentUnit = structure units currentRef.build
	lazy val currentProject = currentUnit defined currentRef.project
	lazy val currentLoader: ClassLoader = currentUnit.loader
	def get[T](key: TaskKey[T]): Task[T] = get(key.task)

	/** Gets the value assigned to `key` in the computed settings map.
	* If the project axis is not explicitly specified, it is resolved to be the current project according to the extracted `session`.
	* Other axes are resolved to be `Global` if they are not specified.*/
	def get[T](key: SettingKey[T]) = getOrError(inCurrent(key), key.key)

	/** Gets the value assigned to `key` in the computed settings map wrapped in Some.  If it does not exist, None is returned.
	* If the project axis is not explicitly specified, it is resolved to be the current project according to the extracted `session`.
	* Other axes are resolved to be `Global` if they are not specified.*/
	def getOpt[T](key: SettingKey[T]): Option[T] = structure.data.get(inCurrent(key), key.key)

	private[this] def inCurrent[T](key: SettingKey[T]): Scope  =  if(key.scope.project == This) key.scope.copy(project = Select(currentRef)) else key.scope
	@deprecated("This method does not apply state changes requested during task execution.  Use 'runTask' instead, which does.", "0.11.1")
	def evalTask[T](key: TaskKey[T], state: State): T = runTask(key, state)._2

	/** Runs the task specified by `key` and returns the transformed State and the resulting value of the task.
	* If the project axis is not defined for the key, it is resolved to be the current project.
	* Other axes are resolved to `Global` if unspecified.
	*
	* This method requests execution of only the given task and does not aggregate execution.  See `runAggregated` for that. */
	def runTask[T](key: TaskKey[T], state: State): (State, T) =
	{
			import EvaluateTask._
		val rkey = resolve(key.scopedKey)
		val config = extractedConfig(this, structure)
		val value: Option[(State, Result[T])] = apply(structure, key.task.scopedKey, state, currentRef, config)
		val (newS, result) = getOrError(rkey.scope, rkey.key, value)
		(newS, processResult(result, newS.log))
	}

	/** Runs the tasks selected by aggregating `key` and returns the transformed State.
	* If the project axis is not defined for the key, it is resolved to be the current project.
	* The project axis is what determines where aggregation starts, so ensure this is set to what you want.
	* Other axes are resolved to `Global` if unspecified. */
	def runAggregated[T](key: TaskKey[T], state: State): State =
	{
		val rkey = resolve(key.scopedKey)
		val keys = Aggregation.aggregate(rkey, ScopeMask(), structure.extra)
		val tasks = Act.keyValues(structure)(keys)
		Aggregation.runTasks(state, structure, tasks, DummyTaskMap(Nil), show = false )(showKey)
	}

	private[this] def resolve[T](key: ScopedKey[T]): ScopedKey[T] =
		Project.mapScope(Scope.resolveScope(GlobalScope, currentRef.build, rootProject) )( key.scopedKey )
	private def getOrError[T](scope: Scope, key: AttributeKey[_], value: Option[T])(implicit display: Show[ScopedKey[_]]): T =
		value getOrElse error(display(ScopedKey(scope, key)) + " is undefined.")
	private def getOrError[T](scope: Scope, key: AttributeKey[T])(implicit display: Show[ScopedKey[_]]): T =
		structure.data.get(scope, key) getOrElse error(display(ScopedKey(scope, key)) + " is undefined.")

	def append(settings: Seq[Setting[_]], state: State): State =
	{
		val appendSettings = Load.transformSettings(Load.projectScope(currentRef), currentRef.build, rootProject, settings)
		val newStructure = Load.reapply(session.original ++ appendSettings, structure)
		Project.setProject(session, newStructure, state)
	}
}
