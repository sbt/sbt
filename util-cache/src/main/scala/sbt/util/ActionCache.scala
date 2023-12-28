package sbt.util

import scala.reflect.ClassTag
import scala.annotation.{ meta, StaticAnnotation }
import sjsonnew.{ HashWriter, JsonFormat }
import sjsonnew.support.murmurhash.Hasher
import xsbti.VirtualFile
import java.nio.file.Path
import scala.quoted.{ Expr, FromExpr, ToExpr, Quotes }

object ActionCache:
  def cache[I: HashWriter, O: JsonFormat: ClassTag](
      key: I,
      otherInputs: Long,
      tags: List[CacheLevelTag],
  )(
      action: I => (O, Seq[VirtualFile])
  )(
      config: BuildWideCacheConfiguration
  ): ActionResult[O] =
    val hashInput: Array[Long] = Array(otherInputs, Hasher.hashUnsafe[I](key))
    val input = Digest(HashUtil.sha256HashStr(hashInput))
    val store = config.store
    val outputDirectory = config.outputDirectory
    val result: Option[ActionResult[O]] = store.get[O](input)
    result match
      case Some(value) =>
        store.syncBlobs(value.outputFiles, outputDirectory)
        value // return the value
      case None =>
        val (newResult, outputs) = action(key)
        val value = store.put[O](input, newResult, outputs)
        store.syncBlobs(value.outputFiles, outputDirectory)
        value
end ActionCache

class BuildWideCacheConfiguration(
    val store: ActionCacheStore,
    val outputDirectory: Path,
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
