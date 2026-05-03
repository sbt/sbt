/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt
/**
 * Configures forking.
 * @param javaHome The Java installation to use.  If not defined, the Java home for the current process is used.
 * @param outputStrategy Configures the forked standard output and error streams.
                         If not defined, StdoutOutput is used, which maps the forked output to the output of
                         this process and the forked error to the error stream of the forking process.
 * @param bootJars The Vector of jars to put on the forked boot classpath.  By default, this is empty.
 * @param workingDirectory The directory to use as the working directory for the forked process.
                           By default, this is the working directory of the forking process.
 * @param runJVMOptions The options to prepend to all user-specified arguments.  By default, this is empty.
 * @param connectInput If true, the standard input of the forked process is connected to the standard input of this process.  Otherwise, it is connected to an empty input stream.
                       Connecting input streams can be problematic, especially on versions before Java 7.
 * @param envVars The environment variables to provide to the forked process.  By default, none are provided.
 * @param canUseArgumentsFile Use arguments file
 */
final class ForkOptions private (
  val javaHome: Option[java.io.File],
  val outputStrategy: Option[sbt.OutputStrategy],
  val bootJars: Vector[java.io.File],
  val workingDirectory: Option[java.io.File],
  val runJVMOptions: Vector[String],
  val connectInput: Boolean,
  val envVars: scala.collection.immutable.Map[String, String],
  val canUseArgumentsFile: Option[Boolean],
  val connectionTimeout: Option[scala.concurrent.duration.Duration]) extends Serializable {
  
  private def this() = this(None, None, Vector(), None, Vector(), false, Map(), None, None)
  private def this(javaHome: Option[java.io.File], outputStrategy: Option[sbt.OutputStrategy], bootJars: Vector[java.io.File], workingDirectory: Option[java.io.File], runJVMOptions: Vector[String], connectInput: Boolean, envVars: scala.collection.immutable.Map[String, String]) = this(javaHome, outputStrategy, bootJars, workingDirectory, runJVMOptions, connectInput, envVars, None, None)
  private def this(javaHome: Option[java.io.File], outputStrategy: Option[sbt.OutputStrategy], bootJars: Vector[java.io.File], workingDirectory: Option[java.io.File], runJVMOptions: Vector[String], connectInput: Boolean, envVars: scala.collection.immutable.Map[String, String], canUseArgumentsFile: Option[Boolean]) = this(javaHome, outputStrategy, bootJars, workingDirectory, runJVMOptions, connectInput, envVars, canUseArgumentsFile, None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ForkOptions => (this.javaHome == x.javaHome) && (this.outputStrategy == x.outputStrategy) && (this.bootJars == x.bootJars) && (this.workingDirectory == x.workingDirectory) && (this.runJVMOptions == x.runJVMOptions) && (this.connectInput == x.connectInput) && (this.envVars == x.envVars) && (this.canUseArgumentsFile == x.canUseArgumentsFile) && (this.connectionTimeout == x.connectionTimeout)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.ForkOptions".##) + javaHome.##) + outputStrategy.##) + bootJars.##) + workingDirectory.##) + runJVMOptions.##) + connectInput.##) + envVars.##) + canUseArgumentsFile.##) + connectionTimeout.##)
  }
  override def toString: String = {
    "ForkOptions(" + javaHome + ", " + outputStrategy + ", " + bootJars + ", " + workingDirectory + ", " + runJVMOptions + ", " + connectInput + ", " + envVars + ", " + canUseArgumentsFile + ", " + connectionTimeout + ")"
  }
  private def copy(javaHome: Option[java.io.File] = javaHome, outputStrategy: Option[sbt.OutputStrategy] = outputStrategy, bootJars: Vector[java.io.File] = bootJars, workingDirectory: Option[java.io.File] = workingDirectory, runJVMOptions: Vector[String] = runJVMOptions, connectInput: Boolean = connectInput, envVars: scala.collection.immutable.Map[String, String] = envVars, canUseArgumentsFile: Option[Boolean] = canUseArgumentsFile, connectionTimeout: Option[scala.concurrent.duration.Duration] = connectionTimeout): ForkOptions = {
    new ForkOptions(javaHome, outputStrategy, bootJars, workingDirectory, runJVMOptions, connectInput, envVars, canUseArgumentsFile, connectionTimeout)
  }
  def withJavaHome(javaHome: Option[java.io.File]): ForkOptions = {
    copy(javaHome = javaHome)
  }
  def withJavaHome(javaHome: java.io.File): ForkOptions = {
    copy(javaHome = Option(javaHome))
  }
  def withOutputStrategy(outputStrategy: Option[sbt.OutputStrategy]): ForkOptions = {
    copy(outputStrategy = outputStrategy)
  }
  def withOutputStrategy(outputStrategy: sbt.OutputStrategy): ForkOptions = {
    copy(outputStrategy = Option(outputStrategy))
  }
  def withBootJars(bootJars: Vector[java.io.File]): ForkOptions = {
    copy(bootJars = bootJars)
  }
  def withWorkingDirectory(workingDirectory: Option[java.io.File]): ForkOptions = {
    copy(workingDirectory = workingDirectory)
  }
  def withWorkingDirectory(workingDirectory: java.io.File): ForkOptions = {
    copy(workingDirectory = Option(workingDirectory))
  }
  def withRunJVMOptions(runJVMOptions: Vector[String]): ForkOptions = {
    copy(runJVMOptions = runJVMOptions)
  }
  def withConnectInput(connectInput: Boolean): ForkOptions = {
    copy(connectInput = connectInput)
  }
  def withEnvVars(envVars: scala.collection.immutable.Map[String, String]): ForkOptions = {
    copy(envVars = envVars)
  }
  def withCanUseArgumentsFile(canUseArgumentsFile: Option[Boolean]): ForkOptions = {
    copy(canUseArgumentsFile = canUseArgumentsFile)
  }
  def withCanUseArgumentsFile(canUseArgumentsFile: Boolean): ForkOptions = {
    copy(canUseArgumentsFile = Option(canUseArgumentsFile))
  }
  def withConnectionTimeout(connectionTimeout: Option[scala.concurrent.duration.Duration]): ForkOptions = {
    copy(connectionTimeout = connectionTimeout)
  }
  def withConnectionTimeout(connectionTimeout: scala.concurrent.duration.Duration): ForkOptions = {
    copy(connectionTimeout = Option(connectionTimeout))
  }
}
object ForkOptions {
  
  def apply(): ForkOptions = new ForkOptions()
  def apply(javaHome: Option[java.io.File], outputStrategy: Option[sbt.OutputStrategy], bootJars: Vector[java.io.File], workingDirectory: Option[java.io.File], runJVMOptions: Vector[String], connectInput: Boolean, envVars: scala.collection.immutable.Map[String, String]): ForkOptions = new ForkOptions(javaHome, outputStrategy, bootJars, workingDirectory, runJVMOptions, connectInput, envVars)
  def apply(javaHome: java.io.File, outputStrategy: sbt.OutputStrategy, bootJars: Vector[java.io.File], workingDirectory: java.io.File, runJVMOptions: Vector[String], connectInput: Boolean, envVars: scala.collection.immutable.Map[String, String]): ForkOptions = new ForkOptions(Option(javaHome), Option(outputStrategy), bootJars, Option(workingDirectory), runJVMOptions, connectInput, envVars)
  def apply(javaHome: Option[java.io.File], outputStrategy: Option[sbt.OutputStrategy], bootJars: Vector[java.io.File], workingDirectory: Option[java.io.File], runJVMOptions: Vector[String], connectInput: Boolean, envVars: scala.collection.immutable.Map[String, String], canUseArgumentsFile: Option[Boolean]): ForkOptions = new ForkOptions(javaHome, outputStrategy, bootJars, workingDirectory, runJVMOptions, connectInput, envVars, canUseArgumentsFile)
  def apply(javaHome: java.io.File, outputStrategy: sbt.OutputStrategy, bootJars: Vector[java.io.File], workingDirectory: java.io.File, runJVMOptions: Vector[String], connectInput: Boolean, envVars: scala.collection.immutable.Map[String, String], canUseArgumentsFile: Boolean): ForkOptions = new ForkOptions(Option(javaHome), Option(outputStrategy), bootJars, Option(workingDirectory), runJVMOptions, connectInput, envVars, Option(canUseArgumentsFile))
  def apply(javaHome: Option[java.io.File], outputStrategy: Option[sbt.OutputStrategy], bootJars: Vector[java.io.File], workingDirectory: Option[java.io.File], runJVMOptions: Vector[String], connectInput: Boolean, envVars: scala.collection.immutable.Map[String, String], canUseArgumentsFile: Option[Boolean], connectionTimeout: Option[scala.concurrent.duration.Duration]): ForkOptions = new ForkOptions(javaHome, outputStrategy, bootJars, workingDirectory, runJVMOptions, connectInput, envVars, canUseArgumentsFile, connectionTimeout)
  def apply(javaHome: java.io.File, outputStrategy: sbt.OutputStrategy, bootJars: Vector[java.io.File], workingDirectory: java.io.File, runJVMOptions: Vector[String], connectInput: Boolean, envVars: scala.collection.immutable.Map[String, String], canUseArgumentsFile: Boolean, connectionTimeout: scala.concurrent.duration.Duration): ForkOptions = new ForkOptions(Option(javaHome), Option(outputStrategy), bootJars, Option(workingDirectory), runJVMOptions, connectInput, envVars, Option(canUseArgumentsFile), Option(connectionTimeout))
}
