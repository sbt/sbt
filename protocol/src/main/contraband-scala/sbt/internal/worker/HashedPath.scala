/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
final class HashedPath private (
  val path: String,
  val digest: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: HashedPath => (this.path == x.path) && (this.digest == x.digest)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.worker.HashedPath".##) + path.##) + digest.##)
  }
  override def toString: String = {
    "HashedPath(" + path + ", " + digest + ")"
  }
  private[this] def copy(path: String = path, digest: String = digest): HashedPath = {
    new HashedPath(path, digest)
  }
  def withPath(path: String): HashedPath = {
    copy(path = path)
  }
  def withDigest(digest: String): HashedPath = {
    copy(digest = digest)
  }
}
object HashedPath {
  
  def apply(path: String, digest: String): HashedPath = new HashedPath(path, digest)
}
