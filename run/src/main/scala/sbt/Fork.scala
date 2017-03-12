/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah, Vesa Vilhonen
 */
package sbt

import java.io.{ File, OutputStream }
import java.util.Locale

@deprecated("Use ForkOptions", "0.13.0")
trait ForkJava {
  def javaHome: Option[File]
  def outputStrategy: Option[OutputStrategy]
  def connectInput: Boolean
}
@deprecated("Use ForkOptions", "0.13.0")
trait ForkScala extends ForkJava {
  def scalaJars: Iterable[File]
}
@deprecated("Use ForkOptions", "0.13.0")
trait ForkScalaRun extends ForkScala {
  def workingDirectory: Option[File]
  def runJVMOptions: Seq[String]
}

/**
  * Configures forking.
  *
  * @param javaHome The Java installation to use.  If not defined, the Java home for the current process is used.
  * @param outputStrategy Configures the forked standard output and error streams.  If not defined, StdoutOutput is used, which maps the forked output to the output of this process and the forked error to the error stream of the forking process.
  * @param bootJars The list of jars to put on the forked boot classpath.  By default, this is empty.
  * @param workingDirectory The directory to use as the working directory for the forked process.  By default, this is the working directory of the forking process.
  * @param runJVMOptions The options to prepend to all user-specified arguments.  By default, this is empty.
  * @param connectInput If true, the standard input of the forked process is connected to the standard input of this process.  Otherwise, it is connected to an empty input stream.  Connecting input streams can be problematic, especially on versions before Java 7.
  * @param envVars The environment variables to provide to the forked process.  By default, none are provided.
  */
final case class ForkOptions(javaHome: Option[File] = None,
                             outputStrategy: Option[OutputStrategy] = None,
                             bootJars: Seq[File] = Nil,
                             workingDirectory: Option[File] = None,
                             runJVMOptions: Seq[String] = Nil,
                             connectInput: Boolean = false,
                             envVars: Map[String, String] = Map.empty)
    extends ForkScalaRun {
  @deprecated("Use bootJars.", "0.13.0")
  def scalaJars: Iterable[File] = bootJars
}

/** Configures where the standard output and error streams from a forked process go.*/
sealed abstract class OutputStrategy

/**
  * Configures the forked standard output to go to standard output of this process and
  * for the forked standard error to go to the standard error of this process.
  */
case object StdoutOutput extends OutputStrategy

/**
  * Logs the forked standard output at the `info` level and the forked standard error at the `error` level.
  * The output is buffered until the process completes, at which point the logger flushes it (to the screen, for example).
  */
case class BufferedOutput(logger: Logger) extends OutputStrategy

/** Logs the forked standard output at the `info` level and the forked standard error at the `error` level. */
case class LoggedOutput(logger: Logger) extends OutputStrategy

/**
  * Configures the forked standard output to be sent to `output` and the forked standard error
  * to be sent to the standard error of this process.
  */
case class CustomOutput(output: OutputStream) extends OutputStrategy

import java.lang.{ ProcessBuilder => JProcessBuilder }

/**
  * Represents a commad that can be forked.
  *
  * @param commandName The java-like binary to fork.  This is expected to exist in bin/ of the Java home directory.
  * @param runnerClass If Some, this will be prepended to the `arguments` passed to the `apply` or `fork` methods.
  */
sealed class Fork(val commandName: String, val runnerClass: Option[String]) {

  /**
    * Forks the configured process, waits for it to complete, and returns the exit code.
    * The command executed is the `commandName` defined for this Fork instance.
    * It is configured according to `config`.
    * If `runnerClass` is defined for this Fork instance, it is prepended to `arguments` to define the arguments passed to the forked command.
    */
  def apply(config: ForkOptions, arguments: Seq[String]): Int = fork(config, arguments).exitValue()

  /**
    * Forks the configured process and returns a `Process` that can be used to wait for completion or to terminate the forked process.
    * The command executed is the `commandName` defined for this Fork instance.
    * It is configured according to `config`.
    * If `runnerClass` is defined for this Fork instance, it is prepended to `arguments` to define the arguments passed to the forked command.
    */
  def fork(config: ForkOptions, arguments: Seq[String]): Process = {
    import config.{ envVars => env, _ }
    val executable = Fork.javaCommand(javaHome, commandName).getAbsolutePath
    val preOptions = makeOptions(runJVMOptions, bootJars, arguments)
    val (classpathEnv, options) = Fork.fitClasspath(preOptions)
    val command = (executable +: options).toArray
    val builder = new JProcessBuilder(command: _*)
    workingDirectory.foreach(wd => builder.directory(wd))
    val environment = builder.environment
    for ((key, value) <- env)
      environment.put(key, value)
    for (cpenv <- classpathEnv)
      // overriding, not appending, is correct due to the specified priorities of -classpath and CLASSPATH
      environment.put(Fork.ClasspathEnvKey, cpenv)
    outputStrategy.getOrElse(StdoutOutput) match {
      case StdoutOutput           => Process(builder).run(connectInput)
      case BufferedOutput(logger) => Process(builder).runBuffered(logger, connectInput)
      case LoggedOutput(logger)   => Process(builder).run(logger, connectInput)
      case CustomOutput(output)   => (Process(builder) #> output).run(connectInput)
    }
  }
  private[this] def makeOptions(jvmOptions: Seq[String],
                                bootJars: Iterable[File],
                                arguments: Seq[String]): Seq[String] = {
    val boot =
      if (bootJars.isEmpty) None
      else Some("-Xbootclasspath/a:" + bootJars.map(_.getAbsolutePath).mkString(File.pathSeparator))
    jvmOptions ++ boot.toList ++ runnerClass.toList ++ arguments
  }
}
object Fork {
  private val ScalacMainClass = "scala.tools.nsc.Main"
  private val ScalaMainClass = "scala.tools.nsc.MainGenericRunner"
  private val JavaCommandName = "java"

  val java = new ForkJava(JavaCommandName)
  val javac = new ForkJava("javac")
  val scala = new Fork(JavaCommandName, Some(ScalaMainClass))
  val scalac = new Fork(JavaCommandName, Some(ScalacMainClass))

  private val ClasspathEnvKey = "CLASSPATH"
  private[this] val ClasspathOptionLong = "-classpath"
  private[this] val ClasspathOptionShort = "-cp"
  private[this] def isClasspathOption(s: String) = s == ClasspathOptionLong || s == ClasspathOptionShort

  /** Maximum length of classpath string before passing the classpath in an environment variable instead of an option. */
  private[this] val MaxConcatenatedOptionLength = 5000

  private def fitClasspath(options: Seq[String]): (Option[String], Seq[String]) =
    if (isWindows && optionsTooLong(options))
      convertClasspathToEnv(options)
    else
      (None, options)
  private[this] def optionsTooLong(options: Seq[String]): Boolean =
    options.mkString(" ").length > MaxConcatenatedOptionLength

  private[this] val isWindows: Boolean =
    System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows")
  private[this] def convertClasspathToEnv(options: Seq[String]): (Option[String], Seq[String]) = {
    val (preCP, cpAndPost) = options.span(opt => !isClasspathOption(opt))
    val postCP = cpAndPost.drop(2)
    val classpathOption = cpAndPost.drop(1).headOption
    val newOptions = if (classpathOption.isDefined) preCP ++ postCP else options
    (classpathOption, newOptions)
  }

  private def javaCommand(javaHome: Option[File], name: String): File = {
    val home = javaHome.getOrElse(new File(System.getProperty("java.home")))
    new File(new File(home, "bin"), name)
  }

  @deprecated("Use Fork", "0.13.0")
  final class ForkJava(commandName: String) extends Fork(commandName, None) {
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
    def apply(javaHome: Option[File],
              options: Seq[String],
              workingDirectory: Option[File],
              outputStrategy: OutputStrategy): Int =
      apply(javaHome, options, workingDirectory, Map.empty, outputStrategy)

    @deprecated("Use apply(ForkOptions, Seq[String])", "0.13.0")
    def apply(javaHome: Option[File],
              options: Seq[String],
              workingDirectory: Option[File],
              env: Map[String, String],
              outputStrategy: OutputStrategy): Int =
      fork(javaHome, options, workingDirectory, env, false, outputStrategy).exitValue

    @deprecated("Use apply(ForkOptions, Seq[String])", "0.13.0")
    def fork(javaHome: Option[File],
             options: Seq[String],
             workingDirectory: Option[File],
             env: Map[String, String],
             connectInput: Boolean,
             outputStrategy: OutputStrategy): Process = {
      val executable = javaCommand(javaHome, commandName).getAbsolutePath
      val command = (executable :: options.toList).toArray
      val builder = new JProcessBuilder(command: _*)
      workingDirectory.foreach(wd => builder.directory(wd))
      val environment = builder.environment
      for ((key, value) <- env)
        environment.put(key, value)
      outputStrategy match {
        case StdoutOutput           => Process(builder).run(connectInput)
        case BufferedOutput(logger) => Process(builder).runBuffered(logger, connectInput)
        case LoggedOutput(logger)   => Process(builder).run(logger, connectInput)
        case CustomOutput(output)   => (Process(builder) #> output).run(connectInput)
      }
    }
  }

  @deprecated("Use Fork", "0.13.0")
  final class ForkScala(mainClassName: String) {
    @deprecated("Use apply(ForkOptions, Seq[String])", "0.13.0")
    def apply(javaHome: Option[File],
              jvmOptions: Seq[String],
              scalaJars: Iterable[File],
              arguments: Seq[String],
              log: Logger): Int =
      apply(javaHome, jvmOptions, scalaJars, arguments, None, BufferedOutput(log))

    @deprecated("Use apply(ForkOptions, Seq[String])", "0.13.0")
    def apply(javaHome: Option[File],
              jvmOptions: Seq[String],
              scalaJars: Iterable[File],
              arguments: Seq[String],
              workingDirectory: Option[File],
              log: Logger): Int =
      apply(javaHome, jvmOptions, scalaJars, arguments, workingDirectory, BufferedOutput(log))

    @deprecated("Use apply(ForkOptions, Seq[String])", "0.13.0")
    def apply(javaHome: Option[File],
              jvmOptions: Seq[String],
              scalaJars: Iterable[File],
              arguments: Seq[String],
              workingDirectory: Option[File],
              outputStrategy: OutputStrategy): Int =
      fork(javaHome, jvmOptions, scalaJars, arguments, workingDirectory, false, outputStrategy).exitValue()

    @deprecated("Use fork(ForkOptions, Seq[String])", "0.13.0")
    def fork(javaHome: Option[File],
             jvmOptions: Seq[String],
             scalaJars: Iterable[File],
             arguments: Seq[String],
             workingDirectory: Option[File],
             connectInput: Boolean,
             outputStrategy: OutputStrategy): Process =
      fork(
        javaHome,
        jvmOptions,
        scalaJars,
        arguments,
        workingDirectory,
        Map.empty,
        connectInput,
        outputStrategy
      )

    @deprecated("Use apply(ForkOptions, Seq[String])", "0.13.0")
    def fork(javaHome: Option[File],
             jvmOptions: Seq[String],
             scalaJars: Iterable[File],
             arguments: Seq[String],
             workingDirectory: Option[File],
             env: Map[String, String],
             connectInput: Boolean,
             outputStrategy: OutputStrategy): Process = {
      if (scalaJars.isEmpty) sys.error("Scala jars not specified")
      val scalaClasspathString = "-Xbootclasspath/a:" + scalaJars
        .map(_.getAbsolutePath)
        .mkString(File.pathSeparator)
      val mainClass = if (mainClassName.isEmpty) Nil else mainClassName :: Nil
      val options = jvmOptions ++ (scalaClasspathString :: mainClass ::: arguments.toList)
      Fork.java.fork(javaHome, options, workingDirectory, env, connectInput, outputStrategy)
    }
  }
}
