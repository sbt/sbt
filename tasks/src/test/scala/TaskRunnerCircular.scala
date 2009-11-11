import xsbt._

import org.scalacheck._
import Prop._
import TaskGen._

object TaskRunnerCircularTest extends Properties("TaskRunner Circular")
{
	property("Catches circular references") = forAll(MaxTasksGen, MaxWorkersGen) { checkCircularReferences _ }
	property("Allows references to completed tasks") = forAllNoShrink(MaxTasksGen, MaxWorkersGen) { allowedReference _ }
	final def allowedReference(intermediate: Int, workers: Int) =
	{
		val top = Task(intermediate) named("top")
		def iterate(task: Task[Int]): Task[Int] =
			task bind { t =>
				if(t <= 0)
					top
				else
					iterate(Task(t-1) named (t-1).toString)
			}
		try { checkResult(TaskRunner(iterate(top), workers), intermediate) }
		catch { case e: CircularDependency => ("Unexpected exception: " + e) |: false }
	}
	final def checkCircularReferences(intermediate: Int, workers: Int) =
	{
		lazy val top = iterate(Task(intermediate) named"bottom", intermediate)
		def iterate(task: Task[Int], i: Int): Task[Int] =
		{
			lazy val it: Task[Int] =
				task bind { t =>
					if(t <= 0)
						top
					else
						iterate(Task(t-1) named (t-1).toString, i-1)
				} named("it_" + i)
			it
		}
		try { TaskRunner(top, workers); false }
		catch { case TasksFailed(failures) => failures.exists(_.exception.isInstanceOf[CircularDependency]) }
	}
}