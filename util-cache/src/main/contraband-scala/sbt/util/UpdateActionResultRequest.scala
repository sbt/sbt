/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.util
final class UpdateActionResultRequest private (
  val actionDigest: sbt.util.Digest,
  val outputFiles: Vector[xsbti.VirtualFile],
  val exitCode: Option[Int],
  val isExecutable: Vector[Boolean]) extends Serializable {
  
  private def this(actionDigest: sbt.util.Digest) = this(actionDigest, Vector(), None, Vector())
  private def this(actionDigest: sbt.util.Digest, outputFiles: Vector[xsbti.VirtualFile]) = this(actionDigest, outputFiles, None, Vector())
  private def this(actionDigest: sbt.util.Digest, outputFiles: Vector[xsbti.VirtualFile], exitCode: Option[Int]) = this(actionDigest, outputFiles, exitCode, Vector())
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: UpdateActionResultRequest => (this.actionDigest == x.actionDigest) && (this.outputFiles == x.outputFiles) && (this.exitCode == x.exitCode) && (this.isExecutable == x.isExecutable)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.util.UpdateActionResultRequest".##) + actionDigest.##) + outputFiles.##) + exitCode.##) + isExecutable.##)
  }
  override def toString: String = {
    "UpdateActionResultRequest(" + actionDigest + ", " + outputFiles + ", " + exitCode + ", " + isExecutable + ")"
  }
  private[this] def copy(actionDigest: sbt.util.Digest = actionDigest, outputFiles: Vector[xsbti.VirtualFile] = outputFiles, exitCode: Option[Int] = exitCode, isExecutable: Vector[Boolean] = isExecutable): UpdateActionResultRequest = {
    new UpdateActionResultRequest(actionDigest, outputFiles, exitCode, isExecutable)
  }
  def withActionDigest(actionDigest: sbt.util.Digest): UpdateActionResultRequest = {
    copy(actionDigest = actionDigest)
  }
  def withOutputFiles(outputFiles: Vector[xsbti.VirtualFile]): UpdateActionResultRequest = {
    copy(outputFiles = outputFiles)
  }
  def withExitCode(exitCode: Option[Int]): UpdateActionResultRequest = {
    copy(exitCode = exitCode)
  }
  def withExitCode(exitCode: Int): UpdateActionResultRequest = {
    copy(exitCode = Option(exitCode))
  }
  def withIsExecutable(isExecutable: Vector[Boolean]): UpdateActionResultRequest = {
    copy(isExecutable = isExecutable)
  }
}
object UpdateActionResultRequest {
  
  def apply(actionDigest: sbt.util.Digest): UpdateActionResultRequest = new UpdateActionResultRequest(actionDigest)
  def apply(actionDigest: sbt.util.Digest, outputFiles: Vector[xsbti.VirtualFile]): UpdateActionResultRequest = new UpdateActionResultRequest(actionDigest, outputFiles)
  def apply(actionDigest: sbt.util.Digest, outputFiles: Vector[xsbti.VirtualFile], exitCode: Option[Int]): UpdateActionResultRequest = new UpdateActionResultRequest(actionDigest, outputFiles, exitCode)
  def apply(actionDigest: sbt.util.Digest, outputFiles: Vector[xsbti.VirtualFile], exitCode: Int): UpdateActionResultRequest = new UpdateActionResultRequest(actionDigest, outputFiles, Option(exitCode))
  def apply(actionDigest: sbt.util.Digest, outputFiles: Vector[xsbti.VirtualFile], exitCode: Option[Int], isExecutable: Vector[Boolean]): UpdateActionResultRequest = new UpdateActionResultRequest(actionDigest, outputFiles, exitCode, isExecutable)
  def apply(actionDigest: sbt.util.Digest, outputFiles: Vector[xsbti.VirtualFile], exitCode: Int, isExecutable: Vector[Boolean]): UpdateActionResultRequest = new UpdateActionResultRequest(actionDigest, outputFiles, Option(exitCode), isExecutable)
}
