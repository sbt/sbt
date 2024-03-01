/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.util
final class GetActionResultRequest private (
  val actionDigest: sbt.util.Digest,
  val inlineStdout: Option[Boolean],
  val inlineStderr: Option[Boolean],
  val inlineOutputFiles: Vector[String]) extends Serializable {
  
  private def this(actionDigest: sbt.util.Digest) = this(actionDigest, None, None, Vector())
  private def this(actionDigest: sbt.util.Digest, inlineStdout: Option[Boolean], inlineStderr: Option[Boolean]) = this(actionDigest, inlineStdout, inlineStderr, Vector())
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: GetActionResultRequest => (this.actionDigest == x.actionDigest) && (this.inlineStdout == x.inlineStdout) && (this.inlineStderr == x.inlineStderr) && (this.inlineOutputFiles == x.inlineOutputFiles)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.util.GetActionResultRequest".##) + actionDigest.##) + inlineStdout.##) + inlineStderr.##) + inlineOutputFiles.##)
  }
  override def toString: String = {
    "GetActionResultRequest(" + actionDigest + ", " + inlineStdout + ", " + inlineStderr + ", " + inlineOutputFiles + ")"
  }
  private[this] def copy(actionDigest: sbt.util.Digest = actionDigest, inlineStdout: Option[Boolean] = inlineStdout, inlineStderr: Option[Boolean] = inlineStderr, inlineOutputFiles: Vector[String] = inlineOutputFiles): GetActionResultRequest = {
    new GetActionResultRequest(actionDigest, inlineStdout, inlineStderr, inlineOutputFiles)
  }
  def withActionDigest(actionDigest: sbt.util.Digest): GetActionResultRequest = {
    copy(actionDigest = actionDigest)
  }
  def withInlineStdout(inlineStdout: Option[Boolean]): GetActionResultRequest = {
    copy(inlineStdout = inlineStdout)
  }
  def withInlineStdout(inlineStdout: Boolean): GetActionResultRequest = {
    copy(inlineStdout = Option(inlineStdout))
  }
  def withInlineStderr(inlineStderr: Option[Boolean]): GetActionResultRequest = {
    copy(inlineStderr = inlineStderr)
  }
  def withInlineStderr(inlineStderr: Boolean): GetActionResultRequest = {
    copy(inlineStderr = Option(inlineStderr))
  }
  def withInlineOutputFiles(inlineOutputFiles: Vector[String]): GetActionResultRequest = {
    copy(inlineOutputFiles = inlineOutputFiles)
  }
}
object GetActionResultRequest {
  
  def apply(actionDigest: sbt.util.Digest): GetActionResultRequest = new GetActionResultRequest(actionDigest)
  def apply(actionDigest: sbt.util.Digest, inlineStdout: Option[Boolean], inlineStderr: Option[Boolean]): GetActionResultRequest = new GetActionResultRequest(actionDigest, inlineStdout, inlineStderr)
  def apply(actionDigest: sbt.util.Digest, inlineStdout: Boolean, inlineStderr: Boolean): GetActionResultRequest = new GetActionResultRequest(actionDigest, Option(inlineStdout), Option(inlineStderr))
  def apply(actionDigest: sbt.util.Digest, inlineStdout: Option[Boolean], inlineStderr: Option[Boolean], inlineOutputFiles: Vector[String]): GetActionResultRequest = new GetActionResultRequest(actionDigest, inlineStdout, inlineStderr, inlineOutputFiles)
  def apply(actionDigest: sbt.util.Digest, inlineStdout: Boolean, inlineStderr: Boolean, inlineOutputFiles: Vector[String]): GetActionResultRequest = new GetActionResultRequest(actionDigest, Option(inlineStdout), Option(inlineStderr), inlineOutputFiles)
}
