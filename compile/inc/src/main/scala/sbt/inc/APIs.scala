/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbti.api.Source
import java.io.File
import APIs.getAPI
import xsbti.api._internalOnly_NameHashes
import scala.util.Sorting
import xsbt.api.SameAPI

trait APIs {
  /**
   * The API for the source file `src` at the time represented by this instance.
   * This method returns an empty API if the file had no API or is not known to this instance.
   */
  def internalAPI(src: File): Source
  /**
   * The API for the external class `ext` at the time represented by this instance.
   * This method returns an empty API if the file had no API or is not known to this instance.
   */
  def externalAPI(ext: String): Source

  def allExternals: collection.Set[String]
  def allInternalSources: collection.Set[File]

  def ++(o: APIs): APIs

  def markInternalSource(src: File, api: Source): APIs
  def markExternalAPI(ext: String, api: Source): APIs

  def removeInternal(remove: Iterable[File]): APIs
  def filterExt(keep: String => Boolean): APIs
  @deprecated("OK to remove in 0.14", "0.13.1")
  def groupBy[K](internal: (File) => K, keepExternal: Map[K, String => Boolean]): Map[K, APIs]

  def internal: Map[File, Source]
  def external: Map[String, Source]

  def hasPackageObject(src: File): Boolean
}
object APIs {
  def apply(internal: Map[File, Source], external: Map[String, Source]): APIs = new MAPIs(internal, external)
  def empty: APIs = apply(Map.empty, Map.empty)

  val emptyAPI = new xsbti.api.SourceAPI(Array(), Array())
  val emptyCompilation = new xsbti.api.Compilation(-1, Array())
  val emptyNameHashes = new xsbti.api._internalOnly_NameHashes(Array.empty, Array.empty)
  val emptySource = new xsbti.api.Source(emptyCompilation, Array(), emptyAPI, 0, emptyNameHashes, false, false)
  def getAPI[T](map: Map[T, Source], src: T): Source = map.getOrElse(src, emptySource)
}

private class MAPIs(val internal: Map[File, Source], val external: Map[String, Source]) extends APIs {
  def allInternalSources: collection.Set[File] = internal.keySet
  def allExternals: collection.Set[String] = external.keySet

  def ++(o: APIs): APIs = new MAPIs(internal ++ o.internal, external ++ o.external)

  def markInternalSource(src: File, api: Source): APIs =
    new MAPIs(internal.updated(src, api), external)

  def markExternalAPI(ext: String, api: Source): APIs =
    new MAPIs(internal, external.updated(ext, api))

  def removeInternal(remove: Iterable[File]): APIs = new MAPIs(internal -- remove, external)
  def filterExt(keep: String => Boolean): APIs = new MAPIs(internal, external.filterKeys(keep))
  @deprecated("Broken implementation. OK to remove in 0.14", "0.13.1")
  def groupBy[K](f: (File) => K, keepExternal: Map[K, String => Boolean]): Map[K, APIs] =
    internal.groupBy(item => f(item._1)) map { group => (group._1, new MAPIs(group._2, external).filterExt(keepExternal.getOrElse(group._1, _ => false))) }

  def internalAPI(src: File) = getAPI(internal, src)
  def externalAPI(ext: String) = getAPI(external, ext)

  override def hasPackageObject(src: File): Boolean = internalAPI(src).hasPackageObject

  override def equals(other: Any): Boolean = other match {
    case o: MAPIs => {
      def areEqual[T](x: Map[T, Source], y: Map[T, Source])(implicit ord: math.Ordering[T]) = {
        x.size == y.size && (sorted(x) zip sorted(y) forall { z => z._1._1 == z._2._1 && SameAPI(z._1._2, z._2._2) })
      }
      areEqual(internal, o.internal) && areEqual(external, o.external)
    }
    case _ => false
  }

  override lazy val hashCode: Int = {
    def hash[T](m: Map[T, Source])(implicit ord: math.Ordering[T]) = sorted(m).map(x => (x._1, x._2.apiHash).hashCode).hashCode
    (hash(internal), hash(external)).hashCode
  }

  override def toString: String = "API(internal: %d, external: %d)".format(internal.size, external.size)

  private[this] def sorted[T](m: Map[T, Source])(implicit ord: math.Ordering[T]): Seq[(T, Source)] = m.toSeq.sortBy(_._1)
}
