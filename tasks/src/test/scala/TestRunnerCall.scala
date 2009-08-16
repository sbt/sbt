import xsbt._

import org.scalacheck._
import Prop._

object TaskRunnerCallTest extends Properties("TaskRunner Call")
{
	specify("calculates fibonacci", (i: Int, workers: Int) =>
		(workers > 0 && i > 0) ==> {
			val f = fibDirect(i)
			("Workers: " + workers) |: ("i: " + i) |: ("fib(i): " + f) |:
			{
				def result = TaskRunner( fibTask(i), workers)
				("Result: " + result) |: (result == Right(f))
			}
		}
	)
	final def fibTask(i: Int) =
	{
		require(i > 0)
		lazy val next: (Int,Int,Int) => Task[Int] =
			(index, x1, x2) =>
			{
				if(index == i)
					Task(x2)
				else
					iterate( (index+1, x2, x1+x2) )
			}
		def iterate(iteration: (Int,Int,Int)) = Task( iteration ) bind Function.tupled(next)
		iterate( (1, 0, 1) )
	}
	final def fibDirect(i: Int): Int =
	{
		require(i > 0)
		def build(index: Int, x1: Int, x2: Int): Int =
			if(index == i)
				x2
			else
				build(index+1, x2, x1+x2)
		build(1, 0, 1)
	}
}