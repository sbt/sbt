import xsbt._

import org.scalacheck._
import Prop._
import TaskGen._

object TaskRunnerSpec extends Properties("TaskRunner")
{
	val iGen = Arbitrary.arbInt.arbitrary
	property("evaluates simple task") = forAll(iGen, MaxWorkersGen) { (i: Int, workers: Int) =>
		("Workers: " + workers) |:
			checkResult(TaskRunner(Task(i), workers), i)
	}
	property("evaluates simple static graph") = forAll(iGen, MaxWorkersGen) { (i: Int, workers: Int) =>
		("Workers: " + workers) |:
		{
			def result = TaskRunner(Task(i) dependsOn(Task(false),Task("a")), workers)
			checkResult(result, i)
		}
	}
	
	property("evaluates simple mapped task") = forAll(iGen, MaxTasksGen, MaxWorkersGen) { (i: Int, times: Int, workers: Int) =>
		("Workers: " + workers) |: ("Value: " + i) |: ("Times: " + times) |:
		{
			def result = TaskRunner(Task(i).map(_*times), workers)
			checkResult(result, i*times)
		}
	}
	property("evaluates chained mapped task") =  forAllNoShrink(iGen, Gen.choose(0, 1000), MaxWorkersGen) { (i: Int, times: Int, workers: Int) =>
		("Workers: " + workers) |: ("Value: " + i) |: ("Times: " + times) |:
		{
			val initial = Task(0) map(identity[Int])
			def task = ( initial /: (0 until times) )( (t,ignore) => t.map(_ + i))
			checkResult(TaskRunner(task, workers), i*times)
		}
	}

	property("evaluates simple bind") = forAll(iGen, MaxTasksGen, MaxWorkersGen) { (i: Int, times: Int, workers: Int) =>
		("Workers: " + workers) |: ("Value: " + i) |: ("Times: " + times) |:
		{
			def result = TaskRunner(Task(i).bind(x => Task(x*times)), workers)
			checkResult(result, i*times)
		}
	}
}