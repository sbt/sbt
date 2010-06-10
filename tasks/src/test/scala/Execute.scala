/* sbt -- Simple Build Tool
 * Copyright 2009, 2010 Mark Harrah
 */
package sbt

import org.scalacheck._
import Prop._
import TaskGen._
import Task._

object ExecuteSpec extends Properties("Execute")
{
	val iGen = Arbitrary.arbInt.arbitrary
	property("evaluates simple task") = forAll(iGen, MaxWorkersGen) { (i: Int, workers: Int) =>
		("Workers: " + workers) |:
			checkResult(tryRun(pure(i), false, workers), i)
	}
	// no direct dependencies currently
	/*property("evaluates simple static graph") = forAll(iGen, MaxWorkersGen) { (i: Int, workers: Int) =>
		("Workers: " + workers) |:
		{
			def result = tryRun(Task(i) dependsOn(pure(false),pure("a")), false, workers)
			checkResult(result, i)
		}
	}*/
	
	property("evaluates simple mapped task") = forAll(iGen, MaxTasksGen, MaxWorkersGen) { (i: Int, times: Int, workers: Int) =>
		("Workers: " + workers) |: ("Value: " + i) |: ("Times: " + times) |:
		{
			def result = tryRun(pure(i).map(_*times), false, workers)
			checkResult(result, i*times)
		}
	}
	property("evaluates chained mapped task") =  forAllNoShrink(iGen, MaxTasksGen, MaxWorkersGen) { (i: Int, times: Int, workers: Int) =>
		("Workers: " + workers) |: ("Value: " + i) |: ("Times: " + times) |:
		{
			val initial = pure(0) map(identity[Int])
			def task = ( initial /: (0 until times) )( (t,ignore) => t.map(_ + i))
			checkResult(tryRun(task, false, workers), i*times)
		}
	}

	property("evaluates simple bind") = forAll(iGen, MaxTasksGen, MaxWorkersGen) { (i: Int, times: Int, workers: Int) =>
		("Workers: " + workers) |: ("Value: " + i) |: ("Times: " + times) |:
		{
			def result = tryRun(pure(i).flatMap(x => pure(x*times)), false, workers)
			checkResult(result, i*times)
		}
	}
}