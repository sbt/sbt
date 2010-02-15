/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */

import sbt._

class SbtTest(info: ProjectInfo) extends PluginProject(info)
{
	val xsbtTest = "org.scala-tools.sbt" %% "test" % version.toString
	val interface = "org.scala-tools.sbt" % "launcher-interface" % version.toString % "provided"

	override def mainResources = super.mainResources +++ "LICENSE" +++ "NOTICE"

	override def managedStyle = ManagedStyle.Ivy
	val publishTo = Resolver.file("technically", new java.io.File("/var/dbwww/repo/"))
}