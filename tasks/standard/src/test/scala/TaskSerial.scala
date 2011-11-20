package sbt
package std

	import Types._
	import TaskExtra._
	import TaskTest.tryRun
	import TaskGen.{MaxWorkers,MaxWorkersGen}

	import org.scalacheck._
	import Prop.forAll
	import Transform.taskToNode
	import ConcurrentRestrictions.{All, completionService, limitTotal, tagged => tagged0, TagMap, unrestricted}

	import java.util.concurrent.{CountDownLatch, TimeUnit}

object TaskSerial extends Properties("task serial")
{
	val checkCycles = true
	val Timeout = 100 // in milliseconds

	def eval[T](t: Task[T]): T = tryRun(t, checkCycles, limitTotal(MaxWorkers))

	property("Evaluates basic") = forAll { (i: Int) =>
		checkResult( eval( task(i) ), i )
	}

	property("Evaluates Function0") = forAll { (i: Int) =>
		checkResult( eval( () => i ), i )
	}

	// verifies that all tasks get scheduled simultaneously (1-3) or do not (4)
	property("Allows arbitrary task limit") = forAll(MaxWorkersGen) { (sze: Int) =>
		val size = math.max(1, sze)
		val halfSize = size / 2 + 1
		val all = 
			checkArbitrary(size, tagged(_ => true), true ) &&
				checkArbitrary(size, unrestricted[Task[_]], true ) &&
				checkArbitrary(size, limitTotal[Task[_]](size), true ) &&
				checkArbitrary(size, limitTotal[Task[_]](halfSize), size <= halfSize )
		all :| ("Size: " + size) :| ("Half size: " + halfSize)
	}

	def checkArbitrary(size: Int, restrictions: ConcurrentRestrictions[Task[_]], shouldSucceed: Boolean) =
	{
		val latch = task { new CountDownLatch(size) }
		def mktask = latch map { l =>
			l.countDown()
			l.await(Timeout, TimeUnit.MILLISECONDS)
		}
		val tasks = (0 until size).map(_ => mktask).toList.join.map { results =>
			val success = results.forall(idFun[Boolean])
			assert( success == shouldSucceed, if(shouldSucceed) unschedulableMsg else scheduledMsg)
		}
		checkResult( evalRestricted( tasks )( restrictions ), () )
	}
	def unschedulableMsg = "Some tasks were unschedulable: verify this is an actual failure by extending the timeout to several seconds."
	def scheduledMsg = "All tasks were unexpectedly scheduled."

	def tagged(f: TagMap => Boolean) = tagged0[Task[_]](_.tags, f)
	def evalRestricted[T](t: Task[T])(restrictions: ConcurrentRestrictions[Task[_]]): T =
		tryRun[T](t, checkCycles, restrictions)
}

object TaskTest
{
	def run[T](root: Task[T], checkCycles: Boolean, restrictions: ConcurrentRestrictions[Task[_]]): Result[T] =
	{
		val (service, shutdown) = completionService[Task[_],Completed](restrictions, (x: String) => System.err.println(x))
		
		val x = new Execute[Task](checkCycles, Execute.noTriggers)(taskToNode)
		try { x.run(root)(service) } finally { shutdown() }
	}
	def tryRun[T](root: Task[T], checkCycles: Boolean, restrictions: ConcurrentRestrictions[Task[_]]): T =
		run(root, checkCycles, restrictions) match {
			case Value(v) => v
			case Inc(i) => throw i
		}
}