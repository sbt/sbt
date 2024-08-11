package sbt.util

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{ Path, Paths }
import sbt.internal.util.{ ActionCacheEvent, CacheEventLog, StringVirtualFile1 }
import sbt.io.syntax.*
import sbt.io.IO
import sbt.nio.file.{ **, FileTreeView }
import sbt.nio.file.syntax.*
import scala.reflect.ClassTag
import scala.annotation.{ meta, StaticAnnotation }
import sjsonnew.{ HashWriter, JsonFormat }
import sjsonnew.support.murmurhash.Hasher
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter, Parser }
import scala.quoted.{ Expr, FromExpr, ToExpr, Quotes }
import xsbti.{ FileConverter, HashedVirtualFileRef, VirtualFile, VirtualFileRef }

object ActionCache:
  private[sbt] val dirZipExt = ".sbtdir.zip"
  private[sbt] val manifestFileName = "sbtdir_manifest.json"

  /**
   * This is a key function that drives remote caching.
   * This is intended to be called from the cached task macro for the most part.
   *
   * - key: This represents the input key for this action, typically consists
   *   of all the input into the action. For the purpose of caching,
   *   all we need from the input is to generate some hash value.
   * - codeContentHash: This hash represents the Scala code of the task.
   *   Even if the input tasks are the same, the code part needs to be tracked.
   * - extraHash: Reserved for later, which we might use to invalidate the cache.
   * - tags: Tags to track cache level.
   * - action: The actual action to be cached.
   * - config: The configuration that's used to store where the cache backends are.
   */
  def cache[I: HashWriter, O: JsonFormat: ClassTag](
      key: I,
      codeContentHash: Digest,
      extraHash: Digest,
      tags: List[CacheLevelTag],
  )(
      action: I => InternalActionResult[O],
  )(
      config: BuildWideCacheConfiguration
  ): O =
    import config.*
    val input =
      Digest.sha256Hash(codeContentHash, extraHash, Digest.dummy(Hasher.hashUnsafe[I](key)))
    val valuePath = s"value/${input}.json"

    def organicTask: O =
      // run action(...) and combine the newResult with outputs
      val InternalActionResult(result, outputs) =
        try action(key): @unchecked
        catch
          case e: Exception =>
            cacheEventLog.append(ActionCacheEvent.Error)
            throw e
      val json = Converter.toJsonUnsafe(result)
      val uncacheableOutputs =
        outputs.filter(f => !fileConverter.toPath(f).startsWith(outputDirectory))
      if uncacheableOutputs.nonEmpty then
        cacheEventLog.append(ActionCacheEvent.Error)
        logger.error(
          s"Cannot cache task because its output files are outside the output directory: \n" +
            uncacheableOutputs.mkString("  - ", "\n  - ", "")
        )
        result
      else
        cacheEventLog.append(ActionCacheEvent.OnsiteTask)
        val valueFile = StringVirtualFile1(s"value/${input}.json", CompactPrinter(json))
        val newOutputs = Vector(valueFile) ++ outputs.toVector
        store.put(UpdateActionResultRequest(input, newOutputs, exitCode = 0)) match
          case Right(cachedResult) =>
            syncBlobs(cachedResult.outputFiles)
            result
          case Left(e) => throw e

    def valueFromStr(str: String, origin: Option[String]): O =
      cacheEventLog.append(ActionCacheEvent.Found(origin.getOrElse("unknown")))
      val json = Parser.parseUnsafe(str)
      Converter.fromJsonUnsafe[O](json)

    def syncBlobs(refs: Seq[HashedVirtualFileRef]): Seq[Path] =
      store.syncBlobs(refs, config.outputDirectory)

    val getRequest =
      GetActionResultRequest(input, inlineStdout = false, inlineStderr = false, Vector(valuePath))
    store.get(getRequest) match
      case Right(result) =>
        // some protocol can embed values into the result
        result.contents.headOption match
          case Some(head) =>
            syncBlobs(result.outputFiles)
            val str = String(head.array(), StandardCharsets.UTF_8)
            valueFromStr(str, result.origin)
          case _ =>
            val paths = syncBlobs(result.outputFiles)
            if paths.isEmpty then organicTask
            else valueFromStr(IO.read(paths.head.toFile()), result.origin)
      case Left(_) => organicTask
  end cache

  def manifestFromFile(manifest: Path): Manifest =
    import sbt.internal.util.codec.ManifestCodec.given
    val json = Parser.parseFromFile(manifest.toFile()).get
    Converter.fromJsonUnsafe[Manifest](json)

  def packageDirectory(
      dir: VirtualFileRef,
      conv: FileConverter,
      outputDirectory: Path,
  ): VirtualFile =
    import sbt.internal.util.codec.ManifestCodec.given
    val dirPath = conv.toPath(dir)
    val allPaths = FileTreeView.default.list(dirPath.toGlob / ** / "*")
    // create a manifest of files and their hashes here
    def makeManifest(manifestFile: Path): Unit =
      val vfs = (allPaths.flatMap {
        case (p, attr) if !attr.isDirectory =>
          Some(conv.toVirtualFile(p): HashedVirtualFileRef)
        case _ => None
      }).toVector
      val manifest = Manifest(
        version = "0.1.0",
        outputFiles = vfs,
      )
      val str = CompactPrinter(Converter.toJsonUnsafe(manifest))
      IO.write(manifestFile.toFile(), str)
    IO.withTemporaryDirectory: tempDir =>
      val mPath = (tempDir / manifestFileName).toPath()
      makeManifest(mPath)
      val zipPath = Paths.get(dirPath.toString + dirZipExt)
      val rebase: Path => Seq[(File, String)] =
        (p: Path) =>
          p match
            case p if p == dirPath => Nil
            case p if p == mPath   => (mPath.toFile() -> manifestFileName) :: Nil
            case f                 => (f.toFile() -> outputDirectory.relativize(f).toString) :: Nil
      IO.zip((allPaths.map(_._1) ++ Seq(mPath)).flatMap(rebase), zipPath.toFile(), None)
      conv.toVirtualFile(zipPath)

  /**
   * Represents a value and output files, used internally by the macro.
   */
  class InternalActionResult[A1] private (
      val value: A1,
      val outputs: Seq[VirtualFile],
  )
  end InternalActionResult
  object InternalActionResult:
    def apply[A1](value: A1, outputs: Seq[VirtualFile]): InternalActionResult[A1] =
      new InternalActionResult(value, outputs)
    private[sbt] def unapply[A1](r: InternalActionResult[A1]): Option[(A1, Seq[VirtualFile])] =
      Some(r.value, r.outputs)
  end InternalActionResult
end ActionCache

class BuildWideCacheConfiguration(
    val store: ActionCacheStore,
    val outputDirectory: Path,
    val fileConverter: FileConverter,
    val logger: Logger,
    val cacheEventLog: CacheEventLog,
):
  override def toString(): String =
    s"BuildWideCacheConfiguration(store = $store, outputDirectory = $outputDirectory)"
end BuildWideCacheConfiguration

@meta.getter
class cacheLevel(
    include: Array[CacheLevelTag],
) extends StaticAnnotation

enum CacheLevelTag:
  case Local
  case Remote
end CacheLevelTag

object CacheLevelTag:
  private[sbt] val all: Array[CacheLevelTag] = Array(CacheLevelTag.Local, CacheLevelTag.Remote)

  given CacheLevelTagToExpr: ToExpr[CacheLevelTag] with
    def apply(tag: CacheLevelTag)(using Quotes): Expr[CacheLevelTag] =
      tag match
        case CacheLevelTag.Local  => '{ CacheLevelTag.Local }
        case CacheLevelTag.Remote => '{ CacheLevelTag.Remote }

  given CacheLevelTagFromExpr: FromExpr[CacheLevelTag] with
    def unapply(expr: Expr[CacheLevelTag])(using Quotes): Option[CacheLevelTag] =
      expr match
        case '{ CacheLevelTag.Local }  => Some(CacheLevelTag.Local)
        case '{ CacheLevelTag.Remote } => Some(CacheLevelTag.Remote)
        case _                         => None
end CacheLevelTag
