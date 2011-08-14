package sbt

import org.scalacheck._
import Prop._
import TaskGen._
import Task._

object TaskRunnerCircularTest extends Properties("TaskRunner Circular")
{
	property("Catches circular references") = forAll(MaxTasksGen, MaxWorkersGen) { checkCircularReferences _ }
	property("Allows references to completed tasks") = forAllNoShrink(MaxTasksGen, MaxWorkersGen) { allowedReference _ }
	final def allowedReference(intermediate: Int, workers: Int) =
	{
		val top = pure("top", intermediate)
		def iterate(task: Task[Int]): Task[Int] =
			task flatMap { t =>
				if(t <= 0)
					top
				else
					iterate(pure((t-1).toString, t-1) )
			}
		try { checkResult(tryRun(iterate(top), true, workers), intermediate) }
		catch { case i: Incomplete if cyclic(i) => ("Unexpected cyclic exception: " + i) |: false }
	}
	final def checkCircularReferences(intermediate: Int, workers: Int) =
	{
		lazy val top = iterate(pure("bottom", intermediate), intermediate)
		def iterate(task: Task[Int], i: Int): Task[Int] =
			task flatMap { t =>
				if(t <= 0)
					top
				else
					iterate(pure((t-1).toString, t-1), i-1)
			}
		try { tryRun(top, true, workers); false }
		catch { case i: Incomplete => cyclic(i) }
	}
	def cyclic(i: Incomplete) = Incomplete.allExceptions(i).exists(_.isInstanceOf[Execute[Task]#CyclicException[_]])
}