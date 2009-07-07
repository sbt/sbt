/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */

import sbt._

class SbtTest(info: ProjectInfo) extends PluginProject(info)
{
	val publishTo = "Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"
	Credentials(Path.fromFile(System.getProperty("user.home")) / ".ivy2" / ".credentials", log)
}