package lmcoursier.definitions

abstract class CacheLogger {
  def foundLocally(url: String): Unit = {}

  def downloadingArtifact(url: String): Unit = {}

  def downloadProgress(url: String, downloaded: Long): Unit = {}

  def downloadedArtifact(url: String, success: Boolean): Unit = {}
  def checkingUpdates(url: String, currentTimeOpt: Option[Long]): Unit = {}
  def checkingUpdatesResult(url: String, currentTimeOpt: Option[Long], remoteTimeOpt: Option[Long]): Unit = {}

  def downloadLength(url: String, totalLength: Long, alreadyDownloaded: Long, watching: Boolean): Unit = {}

  def gettingLength(url: String): Unit = {}
  def gettingLengthResult(url: String, length: Option[Long]): Unit = {}

  def removedCorruptFile(url: String, reason: Option[String]): Unit = {}

  // sizeHint: estimated # of artifacts to be downloaded (doesn't include side stuff like checksums)
  def init(sizeHint: Option[Int] = None): Unit = {}
  def stop(): Unit = {}
}

object CacheLogger {
  def nop: CacheLogger =
    new CacheLogger {}
}
