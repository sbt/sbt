/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package build

import java.io.File

sealed trait LoadCommand
final case class BinaryLoad(classpath: Seq[File], module: Boolean, name: String) extends LoadCommand
final case class SourceLoad(classpath: Seq[File], sourcepath: Seq[File], output: Option[File], module: Boolean, auto: Auto.Value, name: String) extends LoadCommand
final case class ProjectLoad(base: File, auto: Auto.Value, name: String) extends LoadCommand

object Auto extends Enumeration
{
	val Subclass, Annotation, Explicit = Value
}

