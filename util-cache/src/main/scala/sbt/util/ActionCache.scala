package sbt.util

import sbt.internal.util.{ ActionCacheEvent, CacheEventLog, StringVirtualFile1 }
import sbt.io.IO
import scala.reflect.ClassTag
import scala.annotation.{ meta, StaticAnnotation }
import sjsonnew.{ HashWriter, JsonFormat }
import sjsonnew.support.murmurhash.Hasher
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter, Parser }
import xsbti.VirtualFile
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import scala.quoted.{ Expr, FromExpr, ToExpr, Quotes }

object ActionCache:
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
      action: I => (O, Seq[VirtualFile])
  )(
      config: BuildWideCacheConfiguration
  ): O =
    val store = config.store
    val cacheEventLog = config.cacheEventLog
    val input =
      Digest.sha256Hash(codeContentHash, extraHash, Digest.dummy(Hasher.hashUnsafe[I](key)))
    val valuePath = s"value/${input}.json"
    def organicTask: O =
      cacheEventLog.append(ActionCacheEvent.NotFound)
      // run action(...) and combine the newResult with outputs
      val (newResult, outputs) = action(key)
      val json = Converter.toJsonUnsafe(newResult)
      val valueFile = StringVirtualFile1(valuePath, CompactPrinter(json))
      val newOutputs = Vector(valueFile) ++ outputs.toVector
      store.put(UpdateActionResultRequest(input, newOutputs, exitCode = 0)) match
        case Right(result) =>
          store.syncBlobs(result.outputFiles, config.outputDirectory)
          newResult
        case Left(e) => throw e
    def valueFromStr(str: String, origin: Option[String]): O =
      cacheEventLog.append(ActionCacheEvent.Found(origin.getOrElse("unknown")))
      val json = Parser.parseUnsafe(str)
      Converter.fromJsonUnsafe[O](json)
    store.get(
      GetActionResultRequest(input, inlineStdout = false, inlineStderr = false, Vector(valuePath))
    ) match
      case Right(result) =>
        // some protocol can embed values into the result
        result.contents.headOption match
          case Some(head) =>
            store.syncBlobs(result.outputFiles, config.outputDirectory)
            val str = String(head.array(), StandardCharsets.UTF_8)
            valueFromStr(str, result.origin)
          case _ =>
            val paths = store.syncBlobs(result.outputFiles, config.outputDirectory)
            if paths.isEmpty then organicTask
            else valueFromStr(IO.read(paths.head.toFile()), result.origin)
      case Left(_) => organicTask
end ActionCache

class BuildWideCacheConfiguration(
    val store: ActionCacheStore,
    val outputDirectory: Path,
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
