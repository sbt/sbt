package sbt
package internal

import build.bazel.remote.execution.v2.{
  ActionResult as XActionResult,
  Digest as XDigest,
  OutputFile,
}
import sbt.util.{ ActionResult, Digest }
import scala.jdk.CollectionConverters.*
import xsbti.HashedVirtualFileRef

/**
 * Conversions between sbt's cache datatypes and the Remote Execution API messages,
 * shared by the gRPC and the HTTP cache stores so that both write identical entries.
 */
private[sbt] object BazelRemote:
  /** Digest algorithms of the Remote Execution API that sbt can produce, by hex length. */
  private val algoByHexLength: Map[Int, String] = Map(
    40 -> Digest.Sha1,
    64 -> Digest.Sha256,
    96 -> Digest.Sha384,
    128 -> Digest.Sha512,
  )

  def isSupported(algo: String): Boolean =
    algoByHexLength.valuesIterator.contains(algo)

  def isSupported(ref: HashedVirtualFileRef): Boolean =
    isSupported(ref.contentHashStr.takeWhile(_ != '-'))

  def toXActionResult(refs: Seq[HashedVirtualFileRef], exitCode: Option[Int]): XActionResult =
    val b = XActionResult.newBuilder()
    exitCode.foreach: e =>
      b.setExitCode(e)
    refs.foreach: ref =>
      b.addOutputFiles(toOutputFile(ref))
    b.build()

  /** Per spec, clients SHOULD NOT populate contents when uploading to the cache. */
  def toOutputFile(ref: HashedVirtualFileRef): OutputFile =
    val b = OutputFile.newBuilder()
    b.setPath(ref.id)
    b.setDigest(toXDigest(Digest(ref)))
    b.build()

  def toActionResult(ar: XActionResult, storeName: String): ActionResult =
    val outs = ar.getOutputFilesList.asScala.toVector.map: out =>
      val d = toDigest(out.getDigest())
      HashedVirtualFileRef.of(out.getPath(), d.contentHashStr, d.sizeBytes)
    ActionResult(outs, storeName, ar.getExitCode())

  def toXDigest(d: Digest): XDigest =
    val b = XDigest.newBuilder()
    b.setHash(d.hashHexString)
    b.setSizeBytes(d.sizeBytes)
    b.build()

  /** The message carries no algorithm, so it is inferred from the length of the hash. */
  def toDigest(d: XDigest): Digest =
    val hash = d.getHash()
    val algo = algoByHexLength.getOrElse(
      hash.length,
      throw IllegalArgumentException(s"unexpected digest: $hash")
    )
    Digest(s"$algo-$hash/${d.getSizeBytes()}")

  /**
   * Parses key=value headers. Only the first '=' separates the key from the value,
   * since values such as Basic auth credentials end in base64 padding.
   */
  def parseHeaders(remoteHeaders: List[String]): List[(String, String)] =
    remoteHeaders.map: h =>
      h.split("=", 2).toList match
        case List(k, v) => k -> v
        case _          => sys.error("remote header must contain '='")
end BazelRemote
