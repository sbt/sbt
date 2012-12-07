package xsbt.boot

import Pre._
import java.io.File
import java.net.URLClassLoader

final class ModuleDefinition(val configuration: UpdateConfiguration, val extraClasspath: Array[File], val target: UpdateTarget, val failLabel: String)
{
	def retrieveFailed: Nothing = fail("")
	def retrieveCorrupt(missing: Iterable[String]): Nothing = fail(": missing " + missing.mkString(", "))
	private def fail(extra: String) =
		throw new xsbti.RetrieveException(versionString, "Could not retrieve " + failLabel + extra)
	private def versionString: String = target match { case _: UpdateScala => configuration.getScalaVersion; case a: UpdateApp => Value.get(a.id.version) }
}

final class RetrievedModule(val fresh: Boolean, val definition: ModuleDefinition, val detectedScalaVersion: Option[String], val baseDirectories: List[File])
{
	lazy val classpath: Array[File] = getJars(baseDirectories)
	lazy val fullClasspath: Array[File] = concat(classpath, definition.extraClasspath)

	def createLoader(parentLoader: ClassLoader): ClassLoader =
		new URLClassLoader(toURLs(fullClasspath), parentLoader)
}