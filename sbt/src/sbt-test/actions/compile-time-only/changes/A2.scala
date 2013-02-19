import sbt._
import Def.Initialize

object A {
	val x1: Initialize[Task[Int]] = Def.task { 3 }
	val y1 = x1.value
}