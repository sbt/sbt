/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class Checksum private (
  val digest: String,
  val `type`: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Checksum => (this.digest == x.digest) && (this.`type` == x.`type`)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "Checksum".##) + digest.##) + `type`.##)
  }
  override def toString: String = {
    "Checksum(" + digest + ", " + `type` + ")"
  }
  protected[this] def copy(digest: String = digest, `type`: String = `type`): Checksum = {
    new Checksum(digest, `type`)
  }
  def withDigest(digest: String): Checksum = {
    copy(digest = digest)
  }
  def withType(`type`: String): Checksum = {
    copy(`type` = `type`)
  }
}
object Checksum {
  
  def apply(digest: String, `type`: String): Checksum = new Checksum(digest, `type`)
}
