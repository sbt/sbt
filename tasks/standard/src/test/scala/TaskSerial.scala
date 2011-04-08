package sbt
package std

	import Types._
	import TaskExtra._
	import TaskTest.tryRun

	import org.scalacheck._
	import Prop.forAll
	import Transform.taskToNode

object TaskSerial extends Properties("task serial")
{
	val checkCycles = true
	val maxWorkers = 100

	def eval[T](t: Task[T]): T = tryRun(t, checkCycles, maxWorkers)

	property("Evaluates basic") = forAll { (i: Int) =>
		checkResult( eval( task(i) ), i )
	}

	property("Evaluates Function0") = forAll { (i: Int) =>
		checkResult( eval( () => i ), i )
	}

	
}

object TaskTest
{
	def run[T](root: Task[T], checkCycles: Boolean, maxWorkers: Int): Result[T] =
	{
		val (service, shutdown) = CompletionService[Task[_], Completed](maxWorkers)
		
		val x = new Execute[Task](checkCycles)(taskToNode)
		try { x.run(root)(service) } finally { shutdown() }
	}
	def tryRun[T](root: Task[T], checkCycles: Boolean, maxWorkers: Int): T =
		run(root, checkCycles, maxWorkers) match {
			case Value(v) => v
			case Inc(i) => throw i
		}
}