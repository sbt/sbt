import xsbt._

import org.scalacheck._
import Prop._
import Task._
import Math.abs

object TaskRunnerForkTest extends Properties("TaskRunner Fork")
{
	specify("fork m tasks and wait for all to complete", (m: Int, workers: Int) =>
		(workers > 0 && m >= 0) ==> {
			val values = (0 until m).toList
			checkResult(TaskRunner(values.fork(f => () ).join.map(_.toList),workers), values)
			true
		}
	)
	specify("Double join", (a: Int, b: Int, workers: Int) =>
		(workers > 0) ==> { runDoubleJoin(abs(a),abs(b),workers); true }
	)
	def runDoubleJoin(a: Int, b: Int, workers: Int)
	{
		def inner(i: Int) = List.range(0, b).map(j => Task(j) named(j.toString)).join.named("Join " + i)
		TaskRunner( List.range(0,a).map(inner).join.named("Outermost join"), workers)
	}
	specify("fork and reduce", (m: List[Int], workers: Int) => {
			(workers > 0 && !m.isEmpty) ==> {
				val expected = m.reduceLeft(_+_)
				checkResult(TaskRunner( m.reduce(_ + _), workers), expected)
			}
		}
	)
}