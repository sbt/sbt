/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
final class FileConverterConfig private (
  val rootPaths: Vector[sbt.internal.worker.StringURI]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: FileConverterConfig => (this.rootPaths == x.rootPaths)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.worker.FileConverterConfig".##) + rootPaths.##)
  }
  override def toString: String = {
    "FileConverterConfig(" + rootPaths + ")"
  }
  private def copy(rootPaths: Vector[sbt.internal.worker.StringURI]): FileConverterConfig = {
    new FileConverterConfig(rootPaths)
  }
  def withRootPaths(rootPaths: Vector[sbt.internal.worker.StringURI]): FileConverterConfig = {
    copy(rootPaths = rootPaths)
  }
}
object FileConverterConfig {
  
  def apply(rootPaths: Vector[sbt.internal.worker.StringURI]): FileConverterConfig = new FileConverterConfig(rootPaths)
}
