/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah, Vesa Vilhonen
 */
package sbt

import java.io.{File,OutputStream}

@deprecated("Use ForkOptions", "0.13.0")
trait ForkJava
{
	def javaHome: Option[File]
	def outputStrategy: Option[OutputStrategy]
	def connectInput: Boolean
}
@deprecated("Use ForkOptions", "0.13.0")
trait ForkScala extends ForkJava
{
	def scalaJars: Iterable[File]
}
@deprecated("Use ForkOptions", "0.13.0")
trait ForkScalaRun extends ForkScala
{
	def workingDirectory: Option[File]
	def runJVMOptions: Seq[String]
}
final case class ForkOptions(javaHome: Option[File] = None, outputStrategy: Option[OutputStrategy] = None, bootJars: Seq[File] = Nil, workingDirectory: Option[File] = None, runJVMOptions: Seq[String] = Nil, connectInput: Boolean = false, envVars: Map[String,String] = Map.empty) extends ForkScalaRun
{
	@deprecated("Use bootJars.", "0.13.0")
	def scalaJars: Iterable[File] = bootJars
}

sealed abstract class OutputStrategy
case object StdoutOutput extends OutputStrategy
case class BufferedOutput(logger: Logger) extends OutputStrategy
case class LoggedOutput(logger: Logger) extends OutputStrategy
case class CustomOutput(output: OutputStream) extends OutputStrategy

import java.lang.{ProcessBuilder => JProcessBuilder}
sealed class Fork(val commandName: String, val runnerClass: Option[String])
{
	def apply(config: ForkOptions, arguments: Seq[String]): Int = fork(config, arguments).exitValue()
	def fork(config: ForkOptions, arguments: Seq[String]): Process =
	{
			import config.{envVars => env, _}
		val executable = Fork.javaCommand(javaHome, commandName).getAbsolutePath
		val options = makeOptions(runJVMOptions, bootJars, arguments)
		val command = (executable +: options).toArray
		val builder = new JProcessBuilder(command : _*)
		workingDirectory.foreach(wd => builder.directory(wd))
		val environment = builder.environment
		for( (key, value) <- env )
			environment.put(key, value)
		outputStrategy.getOrElse(StdoutOutput) match {
			case StdoutOutput => Process(builder).run(connectInput)
			case BufferedOutput(logger) => Process(builder).runBuffered(logger, connectInput)
			case LoggedOutput(logger) => Process(builder).run(logger, connectInput)
			case CustomOutput(output) => (Process(builder) #> output).run(connectInput)
		}
	}
	private[this] def makeOptions(jvmOptions: Seq[String], bootJars: Iterable[File], arguments: Seq[String]): Seq[String] =
	{
		val boot = if(bootJars.isEmpty) None else Some("-Xbootclasspath/a:" + bootJars.map(_.getAbsolutePath).mkString(File.pathSeparator))
		jvmOptions ++ boot.toList ++ runnerClass.toList ++ arguments
	}
}
object Fork
{
	private val ScalacMainClass = "scala.tools.nsc.Main"
	private val ScalaMainClass = "scala.tools.nsc.MainGenericRunner"
	private val JavaCommandName = "java"

	val java = new ForkJava(JavaCommandName)
	val javac = new ForkJava("javac")
	val scala = new Fork(JavaCommandName, Some(ScalaMainClass))
	val scalac = new Fork(JavaCommandName, Some(ScalacMainClass))

	private def javaCommand(javaHome: Option[File], name: String): File =
	{
		val home = javaHome.getOrElse(new File(System.getProperty("java.home")))
		new File(new File(home, "bin"), name)
	}

	@deprecated("Use Fork", "0.13.0")
	final class ForkJava(commandName: String) extends Fork(commandName, None)
	{
		@deprecated("Use apply(ForkOptions, Seq[String])", "0.13.0")
		def apply(javaHome: Option[File], options: Seq[String], log: Logger): Int =
			apply(javaHome, options, BufferedOutput(log))

		@deprecated("Use apply(ForkOptions, Seq[String])", "0.13.0")
		def apply(javaHome: Option[File], options: Seq[String], outputStrategy: OutputStrategy): Int =
			apply(javaHome, options, None, outputStrategy)

		@deprecated("Use apply(ForkOptions, Seq[String])", "0.13.0")
		def apply(javaHome: Option[File], options: Seq[String], workingDirectory: Option[File], log: Logger): Int =
			apply(javaHome, options, workingDirectory, BufferedOutput(log))

		@deprecated("Use apply(ForkOptions, Seq[String])", "0.13.0")
		def apply(javaHome: Option[File], options: Seq[String], workingDirectory: Option[File], outputStrategy: OutputStrategy): Int =
			apply(javaHome, options, workingDirectory, Map.empty, outputStrategy)

		@deprecated("Use apply(ForkOptions, Seq[String])", "0.13.0")
		def apply(javaHome: Option[File], options: Seq[String], workingDirectory: Option[File], env: Map[String, String], outputStrategy: OutputStrategy): Int =
			fork(javaHome, options, workingDirectory, env, false, outputStrategy).exitValue

		@deprecated("Use apply(ForkOptions, Seq[String])", "0.13.0")
		def fork(javaHome: Option[File], options: Seq[String], workingDirectory: Option[File], env: Map[String, String], connectInput: Boolean, outputStrategy: OutputStrategy): Process =
		{
			val executable = javaCommand(javaHome, commandName).getAbsolutePath
			val command = (executable :: options.toList).toArray
			val builder = new JProcessBuilder(command : _*)
			workingDirectory.foreach(wd => builder.directory(wd))
			val environment = builder.environment
			for( (key, value) <- env )
				environment.put(key, value)
			outputStrategy match {
				case StdoutOutput => Process(builder).run(connectInput)
				case BufferedOutput(logger) => Process(builder).runBuffered(logger, connectInput)
				case LoggedOutput(logger) => Process(builder).run(logger, connectInput)
				case CustomOutput(output) => (Process(builder) #> output).run(connectInput)
			}
		}
	}

	@deprecated("Use Fork", "0.13.0")
	final class ForkScala(mainClassName: String)
	{
		@deprecated("Use apply(ForkOptions, Seq[String])", "0.13.0")
		def apply(javaHome: Option[File], jvmOptions: Seq[String], scalaJars: Iterable[File], arguments: Seq[String], log: Logger): Int =
			apply(javaHome, jvmOptions, scalaJars, arguments, None, BufferedOutput(log))

		@deprecated("Use apply(ForkOptions, Seq[String])", "0.13.0")
		def apply(javaHome: Option[File], jvmOptions: Seq[String], scalaJars: Iterable[File], arguments: Seq[String], workingDirectory: Option[File], log: Logger): Int =
			apply(javaHome, jvmOptions, scalaJars, arguments, workingDirectory, BufferedOutput(log))

		@deprecated("Use apply(ForkOptions, Seq[String])", "0.13.0")
		def apply(javaHome: Option[File], jvmOptions: Seq[String], scalaJars: Iterable[File], arguments: Seq[String], workingDirectory: Option[File], outputStrategy: OutputStrategy): Int =
			fork(javaHome, jvmOptions, scalaJars, arguments, workingDirectory, false, outputStrategy).exitValue()

		@deprecated("Use fork(ForkOptions, Seq[String])", "0.13.0")
		def fork(javaHome: Option[File], jvmOptions: Seq[String], scalaJars: Iterable[File], arguments: Seq[String], workingDirectory: Option[File], connectInput: Boolean, outputStrategy: OutputStrategy): Process =
			fork(javaHome, jvmOptions, scalaJars, arguments, workingDirectory, connectInput, outputStrategy)

		@deprecated("Use apply(ForkOptions, Seq[String])", "0.13.0")
		def fork(javaHome: Option[File], jvmOptions: Seq[String], scalaJars: Iterable[File], arguments: Seq[String], workingDirectory: Option[File], env: Map[String,String], connectInput: Boolean, outputStrategy: OutputStrategy): Process =
		{
			if(scalaJars.isEmpty) sys.error("Scala jars not specified")
			val scalaClasspathString = "-Xbootclasspath/a:" + scalaJars.map(_.getAbsolutePath).mkString(File.pathSeparator)
			val mainClass = if(mainClassName.isEmpty) Nil else mainClassName :: Nil
			val options = jvmOptions ++ (scalaClasspathString :: mainClass ::: arguments.toList)
			Fork.java.fork(javaHome, options, workingDirectory, env, connectInput, outputStrategy)
		}
	}
}
