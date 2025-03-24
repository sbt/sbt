/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
final class NativeRunInfo private (
  val cmd: String,
  val args: Vector[String],
  val connectInput: Boolean,
  val outputStrategy: Option[String],
  val workingDirectory: Option[java.net.URI],
  val environmentVariables: scala.collection.immutable.Map[String, String],
  val inputs: Vector[sbt.internal.worker.FilePath],
  val outputs: Vector[sbt.internal.worker.FilePath]) extends Serializable {
  
  private def this(cmd: String, args: Vector[String], connectInput: Boolean, outputStrategy: Option[String], workingDirectory: Option[java.net.URI], environmentVariables: scala.collection.immutable.Map[String, String]) = this(cmd, args, connectInput, outputStrategy, workingDirectory, environmentVariables, Vector(), Vector())
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: NativeRunInfo => (this.cmd == x.cmd) && (this.args == x.args) && (this.connectInput == x.connectInput) && (this.outputStrategy == x.outputStrategy) && (this.workingDirectory == x.workingDirectory) && (this.environmentVariables == x.environmentVariables) && (this.inputs == x.inputs) && (this.outputs == x.outputs)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.worker.NativeRunInfo".##) + cmd.##) + args.##) + connectInput.##) + outputStrategy.##) + workingDirectory.##) + environmentVariables.##) + inputs.##) + outputs.##)
  }
  override def toString: String = {
    "NativeRunInfo(" + cmd + ", " + args + ", " + connectInput + ", " + outputStrategy + ", " + workingDirectory + ", " + environmentVariables + ", " + inputs + ", " + outputs + ")"
  }
  private def copy(cmd: String = cmd, args: Vector[String] = args, connectInput: Boolean = connectInput, outputStrategy: Option[String] = outputStrategy, workingDirectory: Option[java.net.URI] = workingDirectory, environmentVariables: scala.collection.immutable.Map[String, String] = environmentVariables, inputs: Vector[sbt.internal.worker.FilePath] = inputs, outputs: Vector[sbt.internal.worker.FilePath] = outputs): NativeRunInfo = {
    new NativeRunInfo(cmd, args, connectInput, outputStrategy, workingDirectory, environmentVariables, inputs, outputs)
  }
  def withCmd(cmd: String): NativeRunInfo = {
    copy(cmd = cmd)
  }
  def withArgs(args: Vector[String]): NativeRunInfo = {
    copy(args = args)
  }
  def withConnectInput(connectInput: Boolean): NativeRunInfo = {
    copy(connectInput = connectInput)
  }
  def withOutputStrategy(outputStrategy: Option[String]): NativeRunInfo = {
    copy(outputStrategy = outputStrategy)
  }
  def withOutputStrategy(outputStrategy: String): NativeRunInfo = {
    copy(outputStrategy = Option(outputStrategy))
  }
  def withWorkingDirectory(workingDirectory: Option[java.net.URI]): NativeRunInfo = {
    copy(workingDirectory = workingDirectory)
  }
  def withWorkingDirectory(workingDirectory: java.net.URI): NativeRunInfo = {
    copy(workingDirectory = Option(workingDirectory))
  }
  def withEnvironmentVariables(environmentVariables: scala.collection.immutable.Map[String, String]): NativeRunInfo = {
    copy(environmentVariables = environmentVariables)
  }
  def withInputs(inputs: Vector[sbt.internal.worker.FilePath]): NativeRunInfo = {
    copy(inputs = inputs)
  }
  def withOutputs(outputs: Vector[sbt.internal.worker.FilePath]): NativeRunInfo = {
    copy(outputs = outputs)
  }
}
object NativeRunInfo {
  
  def apply(cmd: String, args: Vector[String], connectInput: Boolean, outputStrategy: Option[String], workingDirectory: Option[java.net.URI], environmentVariables: scala.collection.immutable.Map[String, String]): NativeRunInfo = new NativeRunInfo(cmd, args, connectInput, outputStrategy, workingDirectory, environmentVariables)
  def apply(cmd: String, args: Vector[String], connectInput: Boolean, outputStrategy: String, workingDirectory: java.net.URI, environmentVariables: scala.collection.immutable.Map[String, String]): NativeRunInfo = new NativeRunInfo(cmd, args, connectInput, Option(outputStrategy), Option(workingDirectory), environmentVariables)
  def apply(cmd: String, args: Vector[String], connectInput: Boolean, outputStrategy: Option[String], workingDirectory: Option[java.net.URI], environmentVariables: scala.collection.immutable.Map[String, String], inputs: Vector[sbt.internal.worker.FilePath], outputs: Vector[sbt.internal.worker.FilePath]): NativeRunInfo = new NativeRunInfo(cmd, args, connectInput, outputStrategy, workingDirectory, environmentVariables, inputs, outputs)
  def apply(cmd: String, args: Vector[String], connectInput: Boolean, outputStrategy: String, workingDirectory: java.net.URI, environmentVariables: scala.collection.immutable.Map[String, String], inputs: Vector[sbt.internal.worker.FilePath], outputs: Vector[sbt.internal.worker.FilePath]): NativeRunInfo = new NativeRunInfo(cmd, args, connectInput, Option(outputStrategy), Option(workingDirectory), environmentVariables, inputs, outputs)
}
