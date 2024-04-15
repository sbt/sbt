/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.util
/**
 * An ActionResult represents a result from executing a task.
 * In addition to the value typically represented in the return type
 * of a task, ActionResult tracks the file output and other side effects.
 * 
 * See also https://github.com/bazelbuild/remote-apis/blob/96942a2107c702ed3ca4a664f7eeb7c85ba8dc77/build/bazel/remote/execution/v2/remote_execution.proto#L1056
 */
final class ActionResult private (
  val outputFiles: Vector[xsbti.HashedVirtualFileRef],
  val origin: Option[String],
  val exitCode: Option[Int],
  val contents: Vector[java.nio.ByteBuffer],
  val isExecutable: Vector[Boolean]) extends Serializable {
  
  private def this() = this(Vector(), None, None, Vector(), Vector())
  private def this(outputFiles: Vector[xsbti.HashedVirtualFileRef]) = this(outputFiles, None, None, Vector(), Vector())
  private def this(outputFiles: Vector[xsbti.HashedVirtualFileRef], origin: Option[String]) = this(outputFiles, origin, None, Vector(), Vector())
  private def this(outputFiles: Vector[xsbti.HashedVirtualFileRef], origin: Option[String], exitCode: Option[Int]) = this(outputFiles, origin, exitCode, Vector(), Vector())
  private def this(outputFiles: Vector[xsbti.HashedVirtualFileRef], origin: Option[String], exitCode: Option[Int], contents: Vector[java.nio.ByteBuffer]) = this(outputFiles, origin, exitCode, contents, Vector())
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ActionResult => (this.outputFiles == x.outputFiles) && (this.origin == x.origin) && (this.exitCode == x.exitCode) && (this.contents == x.contents) && (this.isExecutable == x.isExecutable)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.util.ActionResult".##) + outputFiles.##) + origin.##) + exitCode.##) + contents.##) + isExecutable.##)
  }
  override def toString: String = {
    "ActionResult(" + outputFiles + ", " + origin + ", " + exitCode + ", " + contents + ", " + isExecutable + ")"
  }
  private[this] def copy(outputFiles: Vector[xsbti.HashedVirtualFileRef] = outputFiles, origin: Option[String] = origin, exitCode: Option[Int] = exitCode, contents: Vector[java.nio.ByteBuffer] = contents, isExecutable: Vector[Boolean] = isExecutable): ActionResult = {
    new ActionResult(outputFiles, origin, exitCode, contents, isExecutable)
  }
  def withOutputFiles(outputFiles: Vector[xsbti.HashedVirtualFileRef]): ActionResult = {
    copy(outputFiles = outputFiles)
  }
  def withOrigin(origin: Option[String]): ActionResult = {
    copy(origin = origin)
  }
  def withOrigin(origin: String): ActionResult = {
    copy(origin = Option(origin))
  }
  def withExitCode(exitCode: Option[Int]): ActionResult = {
    copy(exitCode = exitCode)
  }
  def withExitCode(exitCode: Int): ActionResult = {
    copy(exitCode = Option(exitCode))
  }
  def withContents(contents: Vector[java.nio.ByteBuffer]): ActionResult = {
    copy(contents = contents)
  }
  def withIsExecutable(isExecutable: Vector[Boolean]): ActionResult = {
    copy(isExecutable = isExecutable)
  }
}
object ActionResult {
  
  def apply(): ActionResult = new ActionResult()
  def apply(outputFiles: Vector[xsbti.HashedVirtualFileRef]): ActionResult = new ActionResult(outputFiles)
  def apply(outputFiles: Vector[xsbti.HashedVirtualFileRef], origin: Option[String]): ActionResult = new ActionResult(outputFiles, origin)
  def apply(outputFiles: Vector[xsbti.HashedVirtualFileRef], origin: String): ActionResult = new ActionResult(outputFiles, Option(origin))
  def apply(outputFiles: Vector[xsbti.HashedVirtualFileRef], origin: Option[String], exitCode: Option[Int]): ActionResult = new ActionResult(outputFiles, origin, exitCode)
  def apply(outputFiles: Vector[xsbti.HashedVirtualFileRef], origin: String, exitCode: Int): ActionResult = new ActionResult(outputFiles, Option(origin), Option(exitCode))
  def apply(outputFiles: Vector[xsbti.HashedVirtualFileRef], origin: Option[String], exitCode: Option[Int], contents: Vector[java.nio.ByteBuffer]): ActionResult = new ActionResult(outputFiles, origin, exitCode, contents)
  def apply(outputFiles: Vector[xsbti.HashedVirtualFileRef], origin: String, exitCode: Int, contents: Vector[java.nio.ByteBuffer]): ActionResult = new ActionResult(outputFiles, Option(origin), Option(exitCode), contents)
  def apply(outputFiles: Vector[xsbti.HashedVirtualFileRef], origin: Option[String], exitCode: Option[Int], contents: Vector[java.nio.ByteBuffer], isExecutable: Vector[Boolean]): ActionResult = new ActionResult(outputFiles, origin, exitCode, contents, isExecutable)
  def apply(outputFiles: Vector[xsbti.HashedVirtualFileRef], origin: String, exitCode: Int, contents: Vector[java.nio.ByteBuffer], isExecutable: Vector[Boolean]): ActionResult = new ActionResult(outputFiles, Option(origin), Option(exitCode), contents, isExecutable)
}
