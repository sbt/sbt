import xsbt._

import org.scalacheck._
import Prop._

object TaskRunnerSortTest extends Properties("TaskRunnerSort")
{
	specify("sort", (a: Array[Int], workers: Int) =>
		(workers > 0) ==> {
			val sorted = a.toArray
			java.util.Arrays.sort(sorted)
			("Workers: " + workers) |: ("Array: " + a.toList) |:
			{
				def result = TaskRunner( sort(a.toArray), workers)
				checkResult(result.toList, sorted.toList)
			}
		}
	)
	final def sortDirect(a: RandomAccessSeq[Int]): RandomAccessSeq[Int] =
	{
		if(a.length < 2)
			a
		else
		{
			val pivot = a(0)
			val (lt,gte) = a.projection.drop(1).partition(_ < pivot)
			sortDirect(lt) ++ List(pivot) ++ sortDirect(gte)
		}
	}
	final def sort(a: RandomAccessSeq[Int]): Task[RandomAccessSeq[Int]] =
	{
		if(a.length < 2)
			Task(a)
		else
		{
			Task(a) bind { a =>
				val pivot = a(0)
				val (lt,gte) = a.projection.drop(1).partition(_ < pivot)
				(sort(lt), sort(gte)) map { _ ++ List(pivot) ++ _ }
			}
		}
	}
}