/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package build

import java.io.File

sealed trait LoadCommand
final case class BinaryLoad(classpath: Seq[File], module: Boolean, name: String) extends LoadCommand
final case class SourceLoad(classpath: Seq[File], sourcepath: Seq[File], output: Option[File], module: Option[Boolean], auto: Auto.Value, name: String) extends LoadCommand
final case class ProjectLoad(base: File, auto: Auto.Value, name: String) extends LoadCommand

object Auto extends Enumeration
{
	val Subclass, Annotation, Explicit = Value
}

final case class CompileCommand(classpath: Seq[File], sources: Seq[File], output: Option[File], options: Seq[String])
final case class DiscoverCommand(module: Option[Boolean], discovery: inc.Discovery)

final case class ToLoad(name: String, isModule: Boolean = false)
{
	override def toString = tpe + " " + name
	def tpe = if(isModule) "object" else "class"
}