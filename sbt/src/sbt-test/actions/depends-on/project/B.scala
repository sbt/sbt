import sbt._
import Keys._

// tests that errors are properly propagated for dependsOn, map, and flatMap
object B extends Build
{
	lazy val root = Project("root", file(".")) settings(
		a <<= baseDirectory map (b =>  if( (b / "succeed").exists) () else error("fail")),
		b <<= a.task(at => nop dependsOn(at) ),
		c <<= a map { _ => () },
		d <<= a flatMap { _ => task { () } }
	)
	lazy val a = TaskKey[Unit]("a")
	lazy val b = TaskKey[Unit]("b")
	lazy val c = TaskKey[Unit]("c")
	lazy val d = TaskKey[Unit]("d")

	lazy val input = Project("input", file("input")) settings(
		f <<= inputTask { _ map { args => if(args(0) == "succeed") () else error("fail") } },
		j := error("j"),
		g <<= f dependsOn(j),
		h <<= f map { _ => IO.touch(file("h")) }
	)
	lazy val f = InputKey[Unit]("f")
	lazy val g = InputKey[Unit]("g")
	lazy val h = InputKey[Unit]("h")
	lazy val j = TaskKey[Unit]("j")
}