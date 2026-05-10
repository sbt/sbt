/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util
final class SpawnInput private (
  val digest: sbt.util.Digest,
  val codeContentHash: sbt.util.Digest,
  val extraHash: sbt.util.Digest,
  val cacheVersion: Option[Long],
  val str: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: SpawnInput => (this.digest == x.digest) && (this.codeContentHash == x.codeContentHash) && (this.extraHash == x.extraHash) && (this.cacheVersion == x.cacheVersion) && (this.str == x.str)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.util.SpawnInput".##) + digest.##) + codeContentHash.##) + extraHash.##) + cacheVersion.##) + str.##)
  }
  override def toString: String = {
    "SpawnInput(" + digest + ", " + codeContentHash + ", " + extraHash + ", " + cacheVersion + ", " + str + ")"
  }
  private def copy(digest: sbt.util.Digest = digest, codeContentHash: sbt.util.Digest = codeContentHash, extraHash: sbt.util.Digest = extraHash, cacheVersion: Option[Long] = cacheVersion, str: Option[String] = str): SpawnInput = {
    new SpawnInput(digest, codeContentHash, extraHash, cacheVersion, str)
  }
  def withDigest(digest: sbt.util.Digest): SpawnInput = {
    copy(digest = digest)
  }
  def withCodeContentHash(codeContentHash: sbt.util.Digest): SpawnInput = {
    copy(codeContentHash = codeContentHash)
  }
  def withExtraHash(extraHash: sbt.util.Digest): SpawnInput = {
    copy(extraHash = extraHash)
  }
  def withCacheVersion(cacheVersion: Option[Long]): SpawnInput = {
    copy(cacheVersion = cacheVersion)
  }
  def withCacheVersion(cacheVersion: Long): SpawnInput = {
    copy(cacheVersion = Option(cacheVersion))
  }
  def withStr(str: Option[String]): SpawnInput = {
    copy(str = str)
  }
  def withStr(str: String): SpawnInput = {
    copy(str = Option(str))
  }
}
object SpawnInput {
  
  def apply(digest: sbt.util.Digest, codeContentHash: sbt.util.Digest, extraHash: sbt.util.Digest, cacheVersion: Option[Long], str: Option[String]): SpawnInput = new SpawnInput(digest, codeContentHash, extraHash, cacheVersion, str)
  def apply(digest: sbt.util.Digest, codeContentHash: sbt.util.Digest, extraHash: sbt.util.Digest, cacheVersion: Long, str: String): SpawnInput = new SpawnInput(digest, codeContentHash, extraHash, Option(cacheVersion), Option(str))
}
