/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
final class JvmRunInfo private (
  val args: Vector[String],
  val classpath: Vector[sbt.internal.worker.FilePath],
  val mainClass: String,
  val connectInput: Boolean,
  val javaHome: Option[java.net.URI],
  val outputStrategy: Option[String],
  val workingDirectory: Option[java.net.URI],
  val jvmOptions: Vector[String],
  val environmentVariables: scala.collection.immutable.Map[String, String],
  val inputs: Vector[sbt.internal.worker.FilePath],
  val outputs: Vector[sbt.internal.worker.FilePath]) extends Serializable {
  
  private def this(args: Vector[String], classpath: Vector[sbt.internal.worker.FilePath], mainClass: String, connectInput: Boolean, javaHome: Option[java.net.URI], outputStrategy: Option[String], workingDirectory: Option[java.net.URI], jvmOptions: Vector[String], environmentVariables: scala.collection.immutable.Map[String, String]) = this(args, classpath, mainClass, connectInput, javaHome, outputStrategy, workingDirectory, jvmOptions, environmentVariables, Vector(), Vector())
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: JvmRunInfo => (this.args == x.args) && (this.classpath == x.classpath) && (this.mainClass == x.mainClass) && (this.connectInput == x.connectInput) && (this.javaHome == x.javaHome) && (this.outputStrategy == x.outputStrategy) && (this.workingDirectory == x.workingDirectory) && (this.jvmOptions == x.jvmOptions) && (this.environmentVariables == x.environmentVariables) && (this.inputs == x.inputs) && (this.outputs == x.outputs)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.worker.JvmRunInfo".##) + args.##) + classpath.##) + mainClass.##) + connectInput.##) + javaHome.##) + outputStrategy.##) + workingDirectory.##) + jvmOptions.##) + environmentVariables.##) + inputs.##) + outputs.##)
  }
  override def toString: String = {
    "JvmRunInfo(" + args + ", " + classpath + ", " + mainClass + ", " + connectInput + ", " + javaHome + ", " + outputStrategy + ", " + workingDirectory + ", " + jvmOptions + ", " + environmentVariables + ", " + inputs + ", " + outputs + ")"
  }
  private def copy(args: Vector[String] = args, classpath: Vector[sbt.internal.worker.FilePath] = classpath, mainClass: String = mainClass, connectInput: Boolean = connectInput, javaHome: Option[java.net.URI] = javaHome, outputStrategy: Option[String] = outputStrategy, workingDirectory: Option[java.net.URI] = workingDirectory, jvmOptions: Vector[String] = jvmOptions, environmentVariables: scala.collection.immutable.Map[String, String] = environmentVariables, inputs: Vector[sbt.internal.worker.FilePath] = inputs, outputs: Vector[sbt.internal.worker.FilePath] = outputs): JvmRunInfo = {
    new JvmRunInfo(args, classpath, mainClass, connectInput, javaHome, outputStrategy, workingDirectory, jvmOptions, environmentVariables, inputs, outputs)
  }
  def withArgs(args: Vector[String]): JvmRunInfo = {
    copy(args = args)
  }
  def withClasspath(classpath: Vector[sbt.internal.worker.FilePath]): JvmRunInfo = {
    copy(classpath = classpath)
  }
  def withMainClass(mainClass: String): JvmRunInfo = {
    copy(mainClass = mainClass)
  }
  def withConnectInput(connectInput: Boolean): JvmRunInfo = {
    copy(connectInput = connectInput)
  }
  def withJavaHome(javaHome: Option[java.net.URI]): JvmRunInfo = {
    copy(javaHome = javaHome)
  }
  def withJavaHome(javaHome: java.net.URI): JvmRunInfo = {
    copy(javaHome = Option(javaHome))
  }
  def withOutputStrategy(outputStrategy: Option[String]): JvmRunInfo = {
    copy(outputStrategy = outputStrategy)
  }
  def withOutputStrategy(outputStrategy: String): JvmRunInfo = {
    copy(outputStrategy = Option(outputStrategy))
  }
  def withWorkingDirectory(workingDirectory: Option[java.net.URI]): JvmRunInfo = {
    copy(workingDirectory = workingDirectory)
  }
  def withWorkingDirectory(workingDirectory: java.net.URI): JvmRunInfo = {
    copy(workingDirectory = Option(workingDirectory))
  }
  def withJvmOptions(jvmOptions: Vector[String]): JvmRunInfo = {
    copy(jvmOptions = jvmOptions)
  }
  def withEnvironmentVariables(environmentVariables: scala.collection.immutable.Map[String, String]): JvmRunInfo = {
    copy(environmentVariables = environmentVariables)
  }
  def withInputs(inputs: Vector[sbt.internal.worker.FilePath]): JvmRunInfo = {
    copy(inputs = inputs)
  }
  def withOutputs(outputs: Vector[sbt.internal.worker.FilePath]): JvmRunInfo = {
    copy(outputs = outputs)
  }
}
object JvmRunInfo {
  
  def apply(args: Vector[String], classpath: Vector[sbt.internal.worker.FilePath], mainClass: String, connectInput: Boolean, javaHome: Option[java.net.URI], outputStrategy: Option[String], workingDirectory: Option[java.net.URI], jvmOptions: Vector[String], environmentVariables: scala.collection.immutable.Map[String, String]): JvmRunInfo = new JvmRunInfo(args, classpath, mainClass, connectInput, javaHome, outputStrategy, workingDirectory, jvmOptions, environmentVariables)
  def apply(args: Vector[String], classpath: Vector[sbt.internal.worker.FilePath], mainClass: String, connectInput: Boolean, javaHome: java.net.URI, outputStrategy: String, workingDirectory: java.net.URI, jvmOptions: Vector[String], environmentVariables: scala.collection.immutable.Map[String, String]): JvmRunInfo = new JvmRunInfo(args, classpath, mainClass, connectInput, Option(javaHome), Option(outputStrategy), Option(workingDirectory), jvmOptions, environmentVariables)
  def apply(args: Vector[String], classpath: Vector[sbt.internal.worker.FilePath], mainClass: String, connectInput: Boolean, javaHome: Option[java.net.URI], outputStrategy: Option[String], workingDirectory: Option[java.net.URI], jvmOptions: Vector[String], environmentVariables: scala.collection.immutable.Map[String, String], inputs: Vector[sbt.internal.worker.FilePath], outputs: Vector[sbt.internal.worker.FilePath]): JvmRunInfo = new JvmRunInfo(args, classpath, mainClass, connectInput, javaHome, outputStrategy, workingDirectory, jvmOptions, environmentVariables, inputs, outputs)
  def apply(args: Vector[String], classpath: Vector[sbt.internal.worker.FilePath], mainClass: String, connectInput: Boolean, javaHome: java.net.URI, outputStrategy: String, workingDirectory: java.net.URI, jvmOptions: Vector[String], environmentVariables: scala.collection.immutable.Map[String, String], inputs: Vector[sbt.internal.worker.FilePath], outputs: Vector[sbt.internal.worker.FilePath]): JvmRunInfo = new JvmRunInfo(args, classpath, mainClass, connectInput, Option(javaHome), Option(outputStrategy), Option(workingDirectory), jvmOptions, environmentVariables, inputs, outputs)
}
