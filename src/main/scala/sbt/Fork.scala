/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah, Vesa Vilhonen
 */
package sbt

import java.io.{File,OutputStream}

abstract class ForkJava extends NotNull
{
	def javaHome: Option[File] = None
	def outputStrategy: Option[OutputStrategy] = None
}
abstract class ForkScala extends ForkJava
{
	def scalaJars: Iterable[File] = None
}
trait ForkScalaRun extends ForkScala
{
	def workingDirectory: Option[File] = None
	def runJVMOptions: Seq[String] = Nil
}

trait ForkScalaCompiler extends ForkScala
{
	def compileJVMOptions: Seq[String] = Nil
}

sealed abstract class OutputStrategy extends NotNull
case object StdoutOutput extends OutputStrategy
case class BufferedOutput(logger: Logger) extends OutputStrategy
case class LoggedOutput(logger: Logger) extends OutputStrategy
case class CustomOutput(output: OutputStream) extends OutputStrategy

import java.lang.{ProcessBuilder => JProcessBuilder}
object Fork
{
	private val ScalacMainClass = "scala.tools.nsc.Main"
	private val ScalaMainClass = "scala.tools.nsc.MainGenericRunner"

	val java = new ForkJava("java")
	val javac = new ForkJava("javac")
	val scala = new ForkScala(ScalaMainClass)
	val scalac = new ForkScala(ScalacMainClass)

	private def javaCommand(javaHome: Option[File], name: String): File =
	{
		val home = javaHome.getOrElse(new File(System.getProperty("java.home")))
		new File(new File(home, "bin"), name)
	}

	final class ForkJava(commandName: String) extends NotNull
	{
		def apply(javaHome: Option[File], options: Seq[String], log: Logger): Int =
			apply(javaHome, options, None, Map.empty, BufferedOutput(log))
		def apply(javaHome: Option[File], options: Seq[String], workingDirectory: Option[File], env: Map[String, String], outputStrategy: OutputStrategy): Int =
		{
			val executable = javaCommand(javaHome, commandName).getAbsolutePath
			val command = (executable :: options.toList).toArray
			val builder = new JProcessBuilder(command : _*)
			workingDirectory.foreach(wd => builder.directory(wd))
			val environment = builder.environment
			for( (key, value) <- env )
				environment.put(key, value)
			outputStrategy match {
				case StdoutOutput => Process(builder) !
				case BufferedOutput(logger) => Process(builder) ! logger
				case LoggedOutput(logger) => Process(builder).run(logger).exitValue()
				case CustomOutput(output) => (Process(builder) #> output).run.exitValue()
			}
		}
	}

	final class ForkScala(mainClassName: String) extends NotNull
	{
		def apply(javaHome: Option[File], jvmOptions: Seq[String], scalaJars: Iterable[File], arguments: Seq[String], log: Logger): Int =
			apply(javaHome, jvmOptions, scalaJars, arguments, None, BufferedOutput(log))
		def apply(javaHome: Option[File], jvmOptions: Seq[String], scalaJars: Iterable[File], arguments: Seq[String], workingDirectory: Option[File], log: Logger): Int =
			apply(javaHome, jvmOptions, scalaJars, arguments, workingDirectory, BufferedOutput(log))
		def apply(javaHome: Option[File], jvmOptions: Seq[String], scalaJars: Iterable[File], arguments: Seq[String], workingDirectory: Option[File], outputStrategy: OutputStrategy): Int =
		{
			val scalaClasspath =
				if(scalaJars.isEmpty)
					FileUtilities.scalaLibraryJar :: FileUtilities.scalaCompilerJar :: Nil
				else
					scalaJars
			val scalaClasspathString = "-Xbootclasspath/a:" + scalaClasspath.map(_.getAbsolutePath).mkString(File.pathSeparator)
			val mainClass = if(mainClassName.isEmpty) Nil else mainClassName :: Nil
			val options = jvmOptions ++ (scalaClasspathString :: mainClass ::: arguments.toList)
			Fork.java(javaHome, options, workingDirectory, Map.empty, outputStrategy)
		}
	}
}
