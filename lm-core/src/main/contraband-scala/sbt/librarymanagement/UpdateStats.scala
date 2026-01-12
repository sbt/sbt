/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** @param stamp Stamp for cache invalidation across commands. Currently a timestamp, but may transition to content hash. */
final class UpdateStats private (
  val resolveTime: Long,
  val downloadTime: Long,
  val downloadSize: Long,
  val cached: Boolean,
  val stamp: Option[String]) extends Serializable {
  
  private def this(resolveTime: Long, downloadTime: Long, downloadSize: Long, cached: Boolean) = this(resolveTime, downloadTime, downloadSize, cached, None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: UpdateStats => (this.resolveTime == x.resolveTime) && (this.downloadTime == x.downloadTime) && (this.downloadSize == x.downloadSize) && (this.cached == x.cached) && (this.stamp == x.stamp)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.UpdateStats".##) + resolveTime.##) + downloadTime.##) + downloadSize.##) + cached.##) + stamp.##)
  }
  override def toString: String = {
    Seq("Resolve time: " + resolveTime + " ms", "Download time: " + downloadTime + " ms", "Download size: " + downloadSize + " bytes").mkString(", ")
  }
  private def copy(resolveTime: Long = resolveTime, downloadTime: Long = downloadTime, downloadSize: Long = downloadSize, cached: Boolean = cached, stamp: Option[String] = stamp): UpdateStats = {
    new UpdateStats(resolveTime, downloadTime, downloadSize, cached, stamp)
  }
  def withResolveTime(resolveTime: Long): UpdateStats = {
    copy(resolveTime = resolveTime)
  }
  def withDownloadTime(downloadTime: Long): UpdateStats = {
    copy(downloadTime = downloadTime)
  }
  def withDownloadSize(downloadSize: Long): UpdateStats = {
    copy(downloadSize = downloadSize)
  }
  def withCached(cached: Boolean): UpdateStats = {
    copy(cached = cached)
  }
  def withStamp(stamp: Option[String]): UpdateStats = {
    copy(stamp = stamp)
  }
}
object UpdateStats {
  
  def apply(resolveTime: Long, downloadTime: Long, downloadSize: Long, cached: Boolean): UpdateStats = new UpdateStats(resolveTime, downloadTime, downloadSize, cached)
  def apply(resolveTime: Long, downloadTime: Long, downloadSize: Long, cached: Boolean, stamp: Option[String]): UpdateStats = new UpdateStats(resolveTime, downloadTime, downloadSize, cached, stamp)
}
