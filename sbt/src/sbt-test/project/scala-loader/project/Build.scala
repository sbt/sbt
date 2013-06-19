import sbt._
import Keys._

object Build extends Build {

	lazy val checkLoader = TaskKey[Unit]("check-loaders")

	def checkTask = subs.map(sub => scalaInstance in LocalProject(sub.id)).join.map { sis =>
		assert(sis.sliding(2).forall{ case Seq(x,y) => x.loader == y.loader }, "Not all ScalaInstances had the same class loader.")
	}

	override def projects = root +: subs
	lazy val root = Project("root", file(".")).settings( checkLoader <<= checkTask )
	lazy val subs = ( for(i <- 1 to 20) yield newProject(i) ).toSeq

	def newProject(i: Int): Project = Project("x" + i.toString, file(i.toString)).settings(
		scalaVersion := "2.10.2"
	)
}
