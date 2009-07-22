/* sbt -- Simple Build Tool
 * Copyright 2008 David MacIver, Mark Harrah
 */
package sbt

import TaskManager._

trait Described extends NotNull
{
	def description: Option[String]
}
trait TaskManager{
	type ManagerType >: this.type <: TaskManager
	type ManagedTask >: Task <: TaskManager#Task with Dag[ManagedTask]
	/** Creates a task that executes the given action when invoked.*/
	def task(action : => Option[String]) = new Task(None, Nil, false, action)
	/** An interactive task is one that is not executed across all dependent projects when
	* it is called directly.  The dependencies of the task are still invoked across all dependent
	* projects, however. */
	def interactiveTask(action: => Option[String]) = new Task(None, Nil, true, action)
	/** Creates a method task that executes the given action when invoked. */
	def task(action: Array[String] => ManagedTask) = new MethodTask(None, action, Nil)
	
	def taskName(t: Task): Option[String]
	final def taskNameString(task: Task): String = taskName(task).getOrElse(UnnamedName)
	
	/** A method task is an action that has parameters.  Note that it is not a Task, though,
	* because it requires arguments to perform its work.  It therefore cannot be a dependency of
	* a Task..*/
	final class MethodTask(val description: Option[String], action: Array[String] => ManagedTask, getCompletions: => Seq[String]) extends Described
	{
		/** Creates a new method task, identical to this method task, except with thE[String]e given description.*/
		def describedAs(description : String) = new MethodTask(Some(description), action, getCompletions)
		/** Invokes this method task with the given arguments.*/
		def apply(arguments: Array[String]) = action(arguments)
		def manager: ManagerType = TaskManager.this
		def completeWith(add: => Seq[String]) = new MethodTask(description, action, add)
		def completions = getCompletions
	}
	
	sealed class Task(val explicitName: Option[String], val description : Option[String], val dependencies : List[ManagedTask],
		 val interactive: Boolean, action : => Option[String]) extends Dag[ManagedTask] with Described
	{
		def this(description : Option[String], dependencies : List[ManagedTask], interactive: Boolean, action : => Option[String]) =
			this(None, description, dependencies, interactive, action)
		checkTaskDependencies(dependencies)
		def manager: ManagerType = TaskManager.this
		def name = explicitName.getOrElse(taskNameString(this))
		private[sbt] def implicitName = taskName(this)
		def named(name: String) = construct(Some(name), description,dependencies, interactive, action)
		override def toString = "Task " + name
		
		/** Creates a new task, identical to this task, except with the additional dependencies specified.*/
		def dependsOn(tasks : ManagedTask*) = setDependencies(tasks.toList ::: dependencies)
		private[sbt] def setDependencies(dependencyList: List[ManagedTask]) =
		{
			checkTaskDependencies(dependencyList)
			construct(explicitName, description, dependencyList, interactive, action)
		}
		/** Creates a new task, identical to this task, except with the given description.*/
		def describedAs(description : String) = construct(explicitName, Some(description), dependencies, interactive, action);
		private[sbt] def invoke = action;
	
		final def setInteractive = construct(explicitName, description, dependencies, true, action)
		final def run = runSequentially(topologicalSort)
		final def runDependenciesOnly = runSequentially(topologicalSort.dropRight(1))
		private def runSequentially(tasks: List[ManagedTask]) = Control.lazyFold(tasks)(_.invoke)
	
		def &&(that : Task) =
			construct(explicitName, None, dependencies ::: that.dependencies, interactive || that.interactive, this.invoke.orElse(that.invoke))
		
		protected def construct(explicitName: Option[String], description: Option[String], dependencies: List[ManagedTask], interactive: Boolean,
			action : => Option[String]): Task = new Task(explicitName, description, dependencies, interactive, action)
	}
	final class CompoundTask private (explicitName: Option[String], description : Option[String], dependencies : List[ManagedTask], interactive: Boolean,
		action : => Option[String], createWork: => SubWork[Project#Task]) extends Task(description, dependencies, interactive, action)
		with CompoundWork[Project#Task]
	{
		def this(createWork: => SubWork[Project#Task]) = this(None, None, Nil, false, None, createWork)
		override protected def construct(explicitName: Option[String], description: Option[String], dependencies: List[ManagedTask],
			interactive: Boolean, action : => Option[String]) = new CompoundTask(explicitName, description, dependencies, interactive, action, createWork)
		def work = createWork
	}
	def dynamic(createTask: => Project#Task) = new CompoundTask(SubWork[Project#Task](checkDynamic(createTask)))
	
	/** Verifies that the given dynamically created task does not depend on any statically defined tasks.
	* Returns the task if it is valid.*/
	private def checkDynamic(task: Project#Task) =
	{
		for(t <- task.topologicalSort if !(t eq task); staticName <- t.implicitName)
			error("Dynamic task " + task.name + " depends on static task " + staticName)
		task
	}
	private def checkTaskDependencies(dependencyList: List[ManagedTask])
	{
		val nullDependencyIndex = dependencyList.findIndexOf(_ == null)
		require(nullDependencyIndex < 0, "Dependency (at index " + nullDependencyIndex + ") is null.  This may be an initialization issue or a circular dependency.")
		val interactiveDependencyIndex = dependencyList.findIndexOf(_.interactive)
		require(interactiveDependencyIndex < 0, "Dependency (at index " + interactiveDependencyIndex + ") is interactive.  Interactive tasks cannot be dependencies.")
	}
}
object TaskManager
{
	val UnnamedName = "<anonymous>"
}