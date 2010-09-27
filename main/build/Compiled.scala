/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package build

import inc.Analysis
import java.io.File
import Path._

final class Compile(val classpath: Seq[File], val sources: Seq[File], output: Option[File], val options: Seq[String], val configuration: xsbti.AppConfiguration)
{
	val scalaProvider = configuration.provider.scalaProvider
	val launcher = scalaProvider.launcher
	val instance = ScalaInstance(scalaProvider.version, scalaProvider)

	val out = output.getOrElse(configuration.baseDirectory / "target" asFile)
	val target = out / ("scala_" + instance.actualVersion)
	val outputDirectory = target / "classes"
	val cacheDirectory = target / "cache"
	val projectClasspath = outputDirectory.asFile +: classpath
	val compileClasspath = projectClasspath ++ configuration.provider.mainClasspath.toSeq
}
final class Compiled(val config: Compile, val analysis: Analysis)