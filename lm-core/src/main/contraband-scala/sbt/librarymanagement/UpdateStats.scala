/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** @param resolvedAt Timestamp (millis since epoch) when this update was resolved. Used for cross-command cache invalidation. */
final class UpdateStats private (
  val resolveTime: Long,
  val downloadTime: Long,
  val downloadSize: Long,
  val cached: Boolean,
  val resolvedAt: Long) extends Serializable {
  
  private def this(resolveTime: Long, downloadTime: Long, downloadSize: Long, cached: Boolean) = this(resolveTime, downloadTime, downloadSize, cached, 0L)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: UpdateStats => (this.resolveTime == x.resolveTime) && (this.downloadTime == x.downloadTime) && (this.downloadSize == x.downloadSize) && (this.cached == x.cached) && (this.resolvedAt == x.resolvedAt)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.UpdateStats".##) + resolveTime.##) + downloadTime.##) + downloadSize.##) + cached.##) + resolvedAt.##)
  }
  override def toString: String = {
    Seq("Resolve time: " + resolveTime + " ms", "Download time: " + downloadTime + " ms", "Download size: " + downloadSize + " bytes").mkString(", ")
  }
  private def copy(resolveTime: Long = resolveTime, downloadTime: Long = downloadTime, downloadSize: Long = downloadSize, cached: Boolean = cached, resolvedAt: Long = resolvedAt): UpdateStats = {
    new UpdateStats(resolveTime, downloadTime, downloadSize, cached, resolvedAt)
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
  def withResolvedAt(resolvedAt: Long): UpdateStats = {
    copy(resolvedAt = resolvedAt)
  }
}
object UpdateStats {
  
  def apply(resolveTime: Long, downloadTime: Long, downloadSize: Long, cached: Boolean): UpdateStats = new UpdateStats(resolveTime, downloadTime, downloadSize, cached)
  def apply(resolveTime: Long, downloadTime: Long, downloadSize: Long, cached: Boolean, resolvedAt: Long): UpdateStats = new UpdateStats(resolveTime, downloadTime, downloadSize, cached, resolvedAt)
}
