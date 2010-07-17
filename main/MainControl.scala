/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt

import java.io.File

private case class Exit(code: Int) extends xsbti.Exit
{
	require(code >= 0)
}
private class Reboot(val scalaVersion: String, argsList: Seq[String], val app: xsbti.ApplicationID, val baseDirectory: File) extends xsbti.Reboot
{
	def arguments = argsList.toArray
}
private class ApplicationID(delegate: xsbti.ApplicationID, newVersion: String) extends xsbti.ApplicationID
{
	def groupID = delegate.groupID
	def name = delegate.name
	def version = newVersion
	
	def mainClass = delegate.mainClass
	def mainComponents = delegate.mainComponents
	def crossVersioned = delegate.crossVersioned
	
	def classpathExtra = delegate.classpathExtra
}