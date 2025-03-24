/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.util
/** A manifest of cached directory etc. */
final class Manifest private (
  val version: String,
  val outputFiles: Vector[xsbti.HashedVirtualFileRef]) extends Serializable {
  
  private def this(version: String) = this(version, Vector())
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: Manifest => (this.version == x.version) && (this.outputFiles == x.outputFiles)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.util.Manifest".##) + version.##) + outputFiles.##)
  }
  override def toString: String = {
    "Manifest(" + version + ", " + outputFiles + ")"
  }
  private def copy(version: String = version, outputFiles: Vector[xsbti.HashedVirtualFileRef] = outputFiles): Manifest = {
    new Manifest(version, outputFiles)
  }
  def withVersion(version: String): Manifest = {
    copy(version = version)
  }
  def withOutputFiles(outputFiles: Vector[xsbti.HashedVirtualFileRef]): Manifest = {
    copy(outputFiles = outputFiles)
  }
}
object Manifest {
  
  def apply(version: String): Manifest = new Manifest(version)
  def apply(version: String, outputFiles: Vector[xsbti.HashedVirtualFileRef]): Manifest = new Manifest(version, outputFiles)
}
