/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util
/** @param origin The name of the cache store that served a cache hit, for example "disk" or "remote". */
final class SpawnExec private (
  val input: sbt.internal.util.SpawnInput,
  val cacheHit: Boolean,
  val exitCode: Option[Int],
  val outputs: Vector[xsbti.HashedVirtualFileRef],
  val origin: Option[String]) extends Serializable {
  
  private def this(input: sbt.internal.util.SpawnInput, cacheHit: Boolean, exitCode: Option[Int]) = this(input, cacheHit, exitCode, Vector(), None)
  private def this(input: sbt.internal.util.SpawnInput, cacheHit: Boolean, exitCode: Option[Int], outputs: Vector[xsbti.HashedVirtualFileRef]) = this(input, cacheHit, exitCode, outputs, None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: SpawnExec => (this.input == x.input) && (this.cacheHit == x.cacheHit) && (this.exitCode == x.exitCode) && (this.outputs == x.outputs) && (this.origin == x.origin)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.util.SpawnExec".##) + input.##) + cacheHit.##) + exitCode.##) + outputs.##) + origin.##)
  }
  override def toString: String = {
    "SpawnExec(" + input + ", " + cacheHit + ", " + exitCode + ", " + outputs + ", " + origin + ")"
  }
  private def copy(input: sbt.internal.util.SpawnInput = input, cacheHit: Boolean = cacheHit, exitCode: Option[Int] = exitCode, outputs: Vector[xsbti.HashedVirtualFileRef] = outputs, origin: Option[String] = origin): SpawnExec = {
    new SpawnExec(input, cacheHit, exitCode, outputs, origin)
  }
  def withInput(input: sbt.internal.util.SpawnInput): SpawnExec = {
    copy(input = input)
  }
  def withCacheHit(cacheHit: Boolean): SpawnExec = {
    copy(cacheHit = cacheHit)
  }
  def withExitCode(exitCode: Option[Int]): SpawnExec = {
    copy(exitCode = exitCode)
  }
  def withExitCode(exitCode: Int): SpawnExec = {
    copy(exitCode = Option(exitCode))
  }
  def withOutputs(outputs: Vector[xsbti.HashedVirtualFileRef]): SpawnExec = {
    copy(outputs = outputs)
  }
  def withOrigin(origin: Option[String]): SpawnExec = {
    copy(origin = origin)
  }
  def withOrigin(origin: String): SpawnExec = {
    copy(origin = Option(origin))
  }
}
object SpawnExec {
  
  def apply(input: sbt.internal.util.SpawnInput, cacheHit: Boolean, exitCode: Option[Int]): SpawnExec = new SpawnExec(input, cacheHit, exitCode)
  def apply(input: sbt.internal.util.SpawnInput, cacheHit: Boolean, exitCode: Int): SpawnExec = new SpawnExec(input, cacheHit, Option(exitCode))
  def apply(input: sbt.internal.util.SpawnInput, cacheHit: Boolean, exitCode: Option[Int], outputs: Vector[xsbti.HashedVirtualFileRef]): SpawnExec = new SpawnExec(input, cacheHit, exitCode, outputs)
  def apply(input: sbt.internal.util.SpawnInput, cacheHit: Boolean, exitCode: Int, outputs: Vector[xsbti.HashedVirtualFileRef]): SpawnExec = new SpawnExec(input, cacheHit, Option(exitCode), outputs)
  def apply(input: sbt.internal.util.SpawnInput, cacheHit: Boolean, exitCode: Option[Int], outputs: Vector[xsbti.HashedVirtualFileRef], origin: Option[String]): SpawnExec = new SpawnExec(input, cacheHit, exitCode, outputs, origin)
  def apply(input: sbt.internal.util.SpawnInput, cacheHit: Boolean, exitCode: Int, outputs: Vector[xsbti.HashedVirtualFileRef], origin: String): SpawnExec = new SpawnExec(input, cacheHit, Option(exitCode), outputs, Option(origin))
}
