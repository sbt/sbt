package xsbt

import HLists._
import Task._

/** This test just verifies that the HList support compiles.*/
object TListCompileTest
{
	val n = Task(1)
	val s = Task("3")
	val t = Task(true)
	val mapped = (n :: s :: t :: TNil) map { case n :: s :: t :: HNil => n }
	val bound = (n :: s :: t :: TNil) bind { case n :: s :: t :: HNil => (Task(n*4) :: Task("Hi " + t) :: TNil).join }
	
	val plusOne = mapped map { _ + 1 }
	val forkN = plusOne bind { count => (0 until count) fork { i => Task(println(i)) } join }
}