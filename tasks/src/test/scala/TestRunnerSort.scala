/* sbt -- Simple Build Tool
 * Copyright 2009, 2010 Mark Harrah
 */
package sbt

import org.scalacheck._
import Prop._
import TaskGen._
import Task._
import Types._

object TaskRunnerSortTest extends Properties("TaskRunnerSort")
{
	property("sort") = forAll(TaskListGen, MaxWorkersGen) { (list: List[Int], workers: Int) =>
		val a = list.toArray
		val sorted = a.toArray
		java.util.Arrays.sort(sorted)
		("Workers: " + workers) |: ("Array: " + a.toList) |:
		{
			def result = tryRun( sort(a.toSeq), false, if(workers > 0) workers else 1)
			checkResult(result.toList, sorted.toList)
		}
	}
	final def sortDirect(a: Seq[Int]): Seq[Int] =
	{
		if(a.length < 2)
			a
		else
		{
			val pivot = a(0)
			val (lt,gte) = a.view.drop(1).partition(_ < pivot)
			sortDirect(lt) ++ List(pivot) ++ sortDirect(gte)
		}
	}
	final def sort(a: Seq[Int]): Task[Seq[Int]] =
	{
		if(a.length < 200)
			pure(sortDirect(a))
		else
		{
			pure(a) flatMap { a =>
				val pivot = a(0)
				val (lt,gte) = a.view.drop(1).partition(_ < pivot)
				sort(lt) :^: sort(gte) :^: MNil mapH {
					case l :+: g :+: HNil => l ++ List(pivot) ++ g
				}
			}
		}
	}
}