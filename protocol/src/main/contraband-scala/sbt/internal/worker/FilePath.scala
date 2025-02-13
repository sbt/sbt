/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
final class FilePath private (
  val path: java.net.URI,
  val digest: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: FilePath => (this.path == x.path) && (this.digest == x.digest)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.worker.FilePath".##) + path.##) + digest.##)
  }
  override def toString: String = {
    "FilePath(" + path + ", " + digest + ")"
  }
  private def copy(path: java.net.URI = path, digest: String = digest): FilePath = {
    new FilePath(path, digest)
  }
  def withPath(path: java.net.URI): FilePath = {
    copy(path = path)
  }
  def withDigest(digest: String): FilePath = {
    copy(digest = digest)
  }
}
object FilePath {
  
  def apply(path: java.net.URI, digest: String): FilePath = new FilePath(path, digest)
}
