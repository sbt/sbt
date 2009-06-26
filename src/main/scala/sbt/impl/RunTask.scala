/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
 package sbt.impl

import scala.collection.{immutable, mutable}
import scala.collection.Map
import sbt.wrap.Wrappers.identityMap

private[sbt] object RunTask
{
	final type Task = Project#Task
	def apply(root: Task, rootName: String): List[WorkFailure[Task]]  = apply(root, rootName, true)
	def apply(root: Task, rootName: String, parallelExecution: Boolean): List[WorkFailure[Task]] =
		apply(root, rootName, if(parallelExecution) Runtime.getRuntime.availableProcessors else 1)
	def apply(root: Task, rootName: String, maximumTasks: Int): List[WorkFailure[Task]]  = (new RunTask(root, rootName, maximumTasks)).run()
}
import RunTask._
private final class RunTask(root: Task, rootName: String, maximumTasks: Int) extends NotNull
{
	require(maximumTasks >= 1)
	def parallel = maximumTasks > 1
	def multiProject = allProjects.size >= 2
	def run(): List[WorkFailure[Task]]  =
	{
		try
		{
			runTasksExceptRoot() match
			{
				case Nil =>
					val result = runTask(root, rootName)
					result.map( errorMessage => WorkFailure(root, "Error running " + rootName + ": " + errorMessage) ).toList
				case failures => failures
			}
		}
		finally
		{
			for(project <- allProjects; saveError <- project.saveEnvironment)
				project.log.warn("Could not save properties for project " + project.name + ": " + saveError)
		}
	}
	// This runs all tasks except the root.task.
	// It uses a buffered logger in record mode to ensure that all output for a given task is consecutive
	// it ignores the root task so that the root task may be run with buffering disabled so that the output
	// occurs without delay.
	private def runTasksExceptRoot() =
	{
		withBuffered(_.startRecording())
		try { ParallelRunner.run(expandedRoot, expandedTaskName, runIfNotRoot, maximumTasks, (t: Task) => t.manager.log) }
		finally { withBuffered(_.stop()) }
	}
	private def withBuffered(f: BufferedLogger => Unit)
	{
		for(buffered <- bufferedLoggers)
			Control.trap(f(buffered))
	}
	/** Will be called in its own thread. Runs the given task if it is not the root task.*/
	private def runIfNotRoot(action: Task): Option[String] =
	{
		if(isRoot(action))
			None
		else
			runTask(action, expandedTaskName(action))
	}
	private def isRoot(t: Task) = t == expandedRoot
	/** Will be called in its own thread except for the root task. */
	private def runTask(action: Task, actionName: String): Option[String] =
	{
		val label = if(multiProject) (action.manager.name + " / " + actionName) else actionName
		def banner(event: ControlEvent.Value, firstSeparator: String, secondSeparator: String) =
			Control.trap(action.manager.log.control(event, firstSeparator + " " + label + " " + secondSeparator))
		if(parallel)
		{
			try { banner(ControlEvent.Start, "\n  ", "...") }
			finally { flush(action) }
		}
		banner(ControlEvent.Header, "\n==", "==")
		try { action.invoke }
		catch { case e: Exception => action.manager.log.trace(e); Some(e.toString) }
		finally
		{
			banner(ControlEvent.Finish, "==", "==")
			if(parallel)
				flush(action)
		}
	}
	private def trapFinally(toTrap: => Unit)(runFinally: => Unit)
	{
		try { toTrap }
		catch { case e: Exception => () }
		finally { try { runFinally } catch { case e: Exception => () } }
	}
	private def flush(action: Task)
	{
		for(buffered <- bufferedLogger(action.manager))
			Control.trap(flush(buffered))
	}
	private def flush(buffered: BufferedLogger)
	{
		buffered.play()
		buffered.clear()
	}

	/* Most of the following is for implicitly adding dependencies (see the expand method)*/
	private val projectDependencyCache = identityMap[Project, Iterable[Project]]
	private def dependencies(project: Project) = projectDependencyCache.getOrElseUpdate(project, project.topologicalSort.dropRight(1))

	private val expandedCache = identityMap[Task, Task]
	private def expanded(task: Task): Task = expandedCache.getOrElseUpdate(task, expandImpl(task))

	private val expandedTaskNameCache = identityMap[Task, String]
	private def expandedTaskName(task: Task) =
		if(task == expandedRoot)
			rootName
		else
			expandedTaskNameCache.getOrElse(task, task.name)

	private val nameToTaskCache = identityMap[Project, Map[String, Task]]
	private def nameToTaskMap(project: Project): Map[String, Task] = nameToTaskCache.getOrElseUpdate(project, project.tasks)
	private def taskForName(project: Project, name: String): Option[Task] = nameToTaskMap(project).get(name)
	
	private val taskNameCache = identityMap[Project, Map[Task, String]]
	private def taskName(task: Task) =
	{
		val project = task.manager
		taskNameCache.getOrElseUpdate(project, taskNameMap(project)).get(task)
	}
	
	private val expandedRoot = expand(root)
	private val allTasks = expandedRoot.topologicalSort
	private val allProjects = Set(allTasks.map(_.manager).toSeq : _*)
	private val bufferedLoggers = if(parallel) allProjects.toList.flatMap(bufferedLogger) else Nil
	
	/** Adds implicit dependencies, which are tasks with the same name in the project dependencies
	* of the enclosing project of the task.*/
	private def expand(root: Task): Task = expanded(root)
	private def expandImpl(task: Task): Task =
	{
		val nameOption = taskName(task)
		val explicitDependencies = task.dependencies
		val implicitDependencies = nameOption.map(name => dependencies(task.manager).flatMap(noninteractiveTask(name)) ).getOrElse(Nil)
		val allDependencies = mutable.HashSet( (explicitDependencies ++ implicitDependencies).toSeq  : _* )
		val expandedTask = task.setDependencies(allDependencies.toList.map(expanded))
		nameOption.foreach(name => expandedTaskNameCache(expandedTask) = name)
		expandedTask
	}
	private def noninteractiveTask(name: String)(project: Project): Option[Task] =
		taskForName(project, name) flatMap { task =>
			if(task.interactive)
			{
				project.log.debug("Not including task " + name + " in project " + project.name + ": interactive tasks can only be run directly.")
				None
			}
			else
				Some(task)
		}
	private def taskNameMap(project: Project) = mutable.Map(nameToTaskMap(project).map(_.swap).toSeq : _*)
	private def bufferedLogger(project: Project): Option[BufferedLogger] =
		project.log match
		{
			case buffered: BufferedLogger => Some(buffered)
			case _ => None
		}
}