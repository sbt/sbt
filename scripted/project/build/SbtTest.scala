/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */

import sbt._

class SbtTest(info: ProjectInfo) extends PluginProject(info)
{
	val xsbtTest = "org.scala-tools.sbt" %% "test" % version.toString

	override def managedStyle = ManagedStyle.Ivy
	val publishTo = Resolver.file("technically", new File("/var/dbwww/repo/"))
}