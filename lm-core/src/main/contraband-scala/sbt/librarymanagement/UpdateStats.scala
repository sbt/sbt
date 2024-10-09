/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class UpdateStats private (
  val resolveTime: Long,
  val downloadTime: Long,
  val downloadSize: Long,
  val cached: Boolean) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: UpdateStats => (this.resolveTime == x.resolveTime) && (this.downloadTime == x.downloadTime) && (this.downloadSize == x.downloadSize) && (this.cached == x.cached)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.UpdateStats".##) + resolveTime.##) + downloadTime.##) + downloadSize.##) + cached.##)
  }
  override def toString: String = {
    Seq("Resolve time: " + resolveTime + " ms", "Download time: " + downloadTime + " ms", "Download size: " + downloadSize + " bytes").mkString(", ")
  }
  private[this] def copy(resolveTime: Long = resolveTime, downloadTime: Long = downloadTime, downloadSize: Long = downloadSize, cached: Boolean = cached): UpdateStats = {
    new UpdateStats(resolveTime, downloadTime, downloadSize, cached)
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
}
object UpdateStats {
  
  def apply(resolveTime: Long, downloadTime: Long, downloadSize: Long, cached: Boolean): UpdateStats = new UpdateStats(resolveTime, downloadTime, downloadSize, cached)
}
