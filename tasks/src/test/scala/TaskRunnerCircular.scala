import xsbt._

import org.scalacheck._
import Prop._

object TaskRunnerCircularTest extends Properties("TaskRunner Circular")
{
	specify("Catches circular references", (intermediate: Int, workers: Int) =>
		(workers > 0 && intermediate >= 0) ==> checkCircularReferences(intermediate, workers)
	)
	/*specify("Check root complete", (intermediate: Int, workers: Int) =>
		(workers > 0 && intermediate >= 0) ==> checkRootComplete(intermediate, workers)
	)*/
	specify("Allows noncircular references", (intermediate: Int, workers: Int) =>
		(workers > 0 && intermediate >= 0) ==> allowedReference(intermediate, workers)
	)
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
		try { checkResult(TaskRunner(iterate(top), workers), 0) }
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
		TaskRunner(top, workers).fold(_.exists(_.exception.isInstanceOf[CircularDependency]), x => false)
	}
	final def checkRootComplete(intermediate: Int, workers: Int) =
	{
		val top = Task(intermediate)
		def iterate(task: Task[Int]): Task[Int] =
		{
			lazy val it: Task[Int] =
				task bind { t =>
					if(t <= 0)
						it
					else
						iterate(Task(t-1) named (t-1).toString)
				} named("it")
			it
		}
		try { TaskRunner(iterate(top), workers); false }
		catch { case e: CircularDependency => true }
	}
}