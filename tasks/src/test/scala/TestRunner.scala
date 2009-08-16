import xsbt._

import org.scalacheck._
import Prop._

object TaskRunnerSpec extends Properties("TaskRunner")
{
	specify("evaluates simple task", (i: Int, workers: Int) =>
		(workers > 0) ==> {
			("Workers: " + workers) |:
				checkResult(TaskRunner(Task(i), workers), i)
		}
	)
	specify("evaluates simple static graph", (i: Int, workers: Int) =>
		(workers > 0) ==> {
			("Workers: " + workers) |:
			{
				def result = TaskRunner(Task(i) dependsOn(Task(false),Task("a")), workers)
				checkResult(result, i)
			}
		}
	)
	specify("evaluates simple mapped task", (i: Int, times: Int, workers: Int) =>
		(workers > 0) ==> {
			("Workers: " + workers) |: ("Value: " + i) |: ("Times: " + times) |:
			{
				def result = TaskRunner(Task(i).map(_*times), workers)
				checkResult(result, i*times)
			}
		}
	)
	specify("evaluates chained mapped task", (i: Int, times: Int, workers: Int) =>
		(workers > 0 && times >= 0) ==> {
			("Workers: " + workers) |: ("Value: " + i) |: ("Times: " + times) |:
			{
				val initial = Task(0) map(identity[Int])
				def task = ( initial /: (0 until times) )( (t,ignore) => t.map(_ + i))
				checkResult(TaskRunner(task, workers), i*times)
			}
		}
	)

	specify("evaluates simple bind", (i: Int, times: Int, workers: Int) =>
		(workers > 0) ==> {
			("Workers: " + workers) |: ("Value: " + i) |: ("Times: " + times) |:
			{
				def result = TaskRunner(Task(i).bind(x => Task(x*times)), workers)
				checkResult(result, i*times)
			}
		}
	)
}