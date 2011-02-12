/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

	import std._
	import compile.{Discovered,Discovery}

abstract class BasicProject 
{
	override def watchPaths: PathFinder = (info.projectDirectory: Path) * sourceFilter +++ descendents("src","*")

	def javapCompiledTask(conf: Configuration): Task[Unit] =
		javapTask(taskData(fullClasspath(conf)), buildScalaInstance)

	// lazy val test-quick, test-failed, javap, javap-quick, jetty-{run,stop,restart}, prepare-webapp, watch paths
}
