/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** Basic license information for a project module */
final class License private (
  val spdxId: String,
  val uri: java.net.URI,
  val distribution: Option[String],
  val comments: Option[String]) extends Serializable {
  
  private def this(spdxId: String, uri: java.net.URI) = this(spdxId, uri, None, None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: License => (this.spdxId == x.spdxId) && (this.uri == x.uri) && (this.distribution == x.distribution) && (this.comments == x.comments)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.License".##) + spdxId.##) + uri.##) + distribution.##) + comments.##)
  }
  override def toString: String = {
    "License(" + spdxId + ", " + uri + ", " + distribution + ", " + comments + ")"
  }
  private def copy(spdxId: String = spdxId, uri: java.net.URI = uri, distribution: Option[String] = distribution, comments: Option[String] = comments): License = {
    new License(spdxId, uri, distribution, comments)
  }
  def withSpdxId(spdxId: String): License = {
    copy(spdxId = spdxId)
  }
  def withUri(uri: java.net.URI): License = {
    copy(uri = uri)
  }
  def withDistribution(distribution: Option[String]): License = {
    copy(distribution = distribution)
  }
  def withDistribution(distribution: String): License = {
    copy(distribution = Option(distribution))
  }
  def withComments(comments: Option[String]): License = {
    copy(comments = comments)
  }
  def withComments(comments: String): License = {
    copy(comments = Option(comments))
  }
}
object License extends sbt.librarymanagement.LicenseFunctions {
  
  def apply(spdxId: String, uri: java.net.URI): License = new License(spdxId, uri)
  def apply(spdxId: String, uri: java.net.URI, distribution: Option[String], comments: Option[String]): License = new License(spdxId, uri, distribution, comments)
  def apply(spdxId: String, uri: java.net.URI, distribution: String, comments: String): License = new License(spdxId, uri, Option(distribution), Option(comments))
}
