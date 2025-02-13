/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
final class RunInfo private (
  val jvm: Boolean,
  val args: Vector[String],
  val classpath: Vector[sbt.internal.worker.FilePath],
  val mainClass: Option[String],
  val connectInput: Boolean,
  val javaHome: Option[java.net.URI],
  val outputStrategy: Option[String],
  val workingDirectory: Option[java.net.URI],
  val jvmOptions: Vector[String],
  val environmentVariables: scala.collection.immutable.Map[String, String],
  val inputs: Vector[sbt.internal.worker.FilePath],
  val outputs: Vector[sbt.internal.worker.FilePath],
  val cmd: Option[String]) extends Serializable {
  
  private def this(jvm: Boolean, args: Vector[String], classpath: Vector[sbt.internal.worker.FilePath], mainClass: Option[String], connectInput: Boolean, javaHome: Option[java.net.URI], outputStrategy: Option[String], workingDirectory: Option[java.net.URI], jvmOptions: Vector[String], environmentVariables: scala.collection.immutable.Map[String, String]) = this(jvm, args, classpath, mainClass, connectInput, javaHome, outputStrategy, workingDirectory, jvmOptions, environmentVariables, Vector(), Vector(), None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: RunInfo => (this.jvm == x.jvm) && (this.args == x.args) && (this.classpath == x.classpath) && (this.mainClass == x.mainClass) && (this.connectInput == x.connectInput) && (this.javaHome == x.javaHome) && (this.outputStrategy == x.outputStrategy) && (this.workingDirectory == x.workingDirectory) && (this.jvmOptions == x.jvmOptions) && (this.environmentVariables == x.environmentVariables) && (this.inputs == x.inputs) && (this.outputs == x.outputs) && (this.cmd == x.cmd)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.worker.RunInfo".##) + jvm.##) + args.##) + classpath.##) + mainClass.##) + connectInput.##) + javaHome.##) + outputStrategy.##) + workingDirectory.##) + jvmOptions.##) + environmentVariables.##) + inputs.##) + outputs.##) + cmd.##)
  }
  override def toString: String = {
    "RunInfo(" + jvm + ", " + args + ", " + classpath + ", " + mainClass + ", " + connectInput + ", " + javaHome + ", " + outputStrategy + ", " + workingDirectory + ", " + jvmOptions + ", " + environmentVariables + ", " + inputs + ", " + outputs + ", " + cmd + ")"
  }
  private def copy(jvm: Boolean = jvm, args: Vector[String] = args, classpath: Vector[sbt.internal.worker.FilePath] = classpath, mainClass: Option[String] = mainClass, connectInput: Boolean = connectInput, javaHome: Option[java.net.URI] = javaHome, outputStrategy: Option[String] = outputStrategy, workingDirectory: Option[java.net.URI] = workingDirectory, jvmOptions: Vector[String] = jvmOptions, environmentVariables: scala.collection.immutable.Map[String, String] = environmentVariables, inputs: Vector[sbt.internal.worker.FilePath] = inputs, outputs: Vector[sbt.internal.worker.FilePath] = outputs, cmd: Option[String] = cmd): RunInfo = {
    new RunInfo(jvm, args, classpath, mainClass, connectInput, javaHome, outputStrategy, workingDirectory, jvmOptions, environmentVariables, inputs, outputs, cmd)
  }
  def withJvm(jvm: Boolean): RunInfo = {
    copy(jvm = jvm)
  }
  def withArgs(args: Vector[String]): RunInfo = {
    copy(args = args)
  }
  def withClasspath(classpath: Vector[sbt.internal.worker.FilePath]): RunInfo = {
    copy(classpath = classpath)
  }
  def withMainClass(mainClass: Option[String]): RunInfo = {
    copy(mainClass = mainClass)
  }
  def withMainClass(mainClass: String): RunInfo = {
    copy(mainClass = Option(mainClass))
  }
  def withConnectInput(connectInput: Boolean): RunInfo = {
    copy(connectInput = connectInput)
  }
  def withJavaHome(javaHome: Option[java.net.URI]): RunInfo = {
    copy(javaHome = javaHome)
  }
  def withJavaHome(javaHome: java.net.URI): RunInfo = {
    copy(javaHome = Option(javaHome))
  }
  def withOutputStrategy(outputStrategy: Option[String]): RunInfo = {
    copy(outputStrategy = outputStrategy)
  }
  def withOutputStrategy(outputStrategy: String): RunInfo = {
    copy(outputStrategy = Option(outputStrategy))
  }
  def withWorkingDirectory(workingDirectory: Option[java.net.URI]): RunInfo = {
    copy(workingDirectory = workingDirectory)
  }
  def withWorkingDirectory(workingDirectory: java.net.URI): RunInfo = {
    copy(workingDirectory = Option(workingDirectory))
  }
  def withJvmOptions(jvmOptions: Vector[String]): RunInfo = {
    copy(jvmOptions = jvmOptions)
  }
  def withEnvironmentVariables(environmentVariables: scala.collection.immutable.Map[String, String]): RunInfo = {
    copy(environmentVariables = environmentVariables)
  }
  def withInputs(inputs: Vector[sbt.internal.worker.FilePath]): RunInfo = {
    copy(inputs = inputs)
  }
  def withOutputs(outputs: Vector[sbt.internal.worker.FilePath]): RunInfo = {
    copy(outputs = outputs)
  }
  def withCmd(cmd: Option[String]): RunInfo = {
    copy(cmd = cmd)
  }
  def withCmd(cmd: String): RunInfo = {
    copy(cmd = Option(cmd))
  }
}
object RunInfo {
  
  def apply(jvm: Boolean, args: Vector[String], classpath: Vector[sbt.internal.worker.FilePath], mainClass: Option[String], connectInput: Boolean, javaHome: Option[java.net.URI], outputStrategy: Option[String], workingDirectory: Option[java.net.URI], jvmOptions: Vector[String], environmentVariables: scala.collection.immutable.Map[String, String]): RunInfo = new RunInfo(jvm, args, classpath, mainClass, connectInput, javaHome, outputStrategy, workingDirectory, jvmOptions, environmentVariables)
  def apply(jvm: Boolean, args: Vector[String], classpath: Vector[sbt.internal.worker.FilePath], mainClass: String, connectInput: Boolean, javaHome: java.net.URI, outputStrategy: String, workingDirectory: java.net.URI, jvmOptions: Vector[String], environmentVariables: scala.collection.immutable.Map[String, String]): RunInfo = new RunInfo(jvm, args, classpath, Option(mainClass), connectInput, Option(javaHome), Option(outputStrategy), Option(workingDirectory), jvmOptions, environmentVariables)
  def apply(jvm: Boolean, args: Vector[String], classpath: Vector[sbt.internal.worker.FilePath], mainClass: Option[String], connectInput: Boolean, javaHome: Option[java.net.URI], outputStrategy: Option[String], workingDirectory: Option[java.net.URI], jvmOptions: Vector[String], environmentVariables: scala.collection.immutable.Map[String, String], inputs: Vector[sbt.internal.worker.FilePath], outputs: Vector[sbt.internal.worker.FilePath], cmd: Option[String]): RunInfo = new RunInfo(jvm, args, classpath, mainClass, connectInput, javaHome, outputStrategy, workingDirectory, jvmOptions, environmentVariables, inputs, outputs, cmd)
  def apply(jvm: Boolean, args: Vector[String], classpath: Vector[sbt.internal.worker.FilePath], mainClass: String, connectInput: Boolean, javaHome: java.net.URI, outputStrategy: String, workingDirectory: java.net.URI, jvmOptions: Vector[String], environmentVariables: scala.collection.immutable.Map[String, String], inputs: Vector[sbt.internal.worker.FilePath], outputs: Vector[sbt.internal.worker.FilePath], cmd: String): RunInfo = new RunInfo(jvm, args, classpath, Option(mainClass), connectInput, Option(javaHome), Option(outputStrategy), Option(workingDirectory), jvmOptions, environmentVariables, inputs, outputs, Option(cmd))
}
