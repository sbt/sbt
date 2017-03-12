/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import java.io.{ File, IOException }
import Stamp.getStamp
import scala.util.matching.Regex

trait ReadStamps {

  /** The Stamp for the given product at the time represented by this Stamps instance.*/
  def product(prod: File): Stamp

  /** The Stamp for the given source file at the time represented by this Stamps instance.*/
  def internalSource(src: File): Stamp

  /** The Stamp for the given binary dependency at the time represented by this Stamps instance.*/
  def binary(bin: File): Stamp
}

/** Provides information about files as they were at a specific time.*/
trait Stamps extends ReadStamps {
  def allInternalSources: collection.Set[File]
  def allBinaries: collection.Set[File]
  def allProducts: collection.Set[File]

  def sources: Map[File, Stamp]
  def binaries: Map[File, Stamp]
  def products: Map[File, Stamp]
  def classNames: Map[File, String]

  def className(bin: File): Option[String]

  def markInternalSource(src: File, s: Stamp): Stamps
  def markBinary(bin: File, className: String, s: Stamp): Stamps
  def markProduct(prod: File, s: Stamp): Stamps

  def filter(prod: File => Boolean, removeSources: Iterable[File], bin: File => Boolean): Stamps

  def ++(o: Stamps): Stamps
  def groupBy[K](prod: Map[K, File => Boolean],
                 sourcesGrouping: File => K,
                 bin: Map[K, File => Boolean]): Map[K, Stamps]
}

sealed trait Stamp {
  override def equals(other: Any): Boolean = other match {
    case o: Stamp => Stamp.equivStamp.equiv(this, o)
    case _        => false
  }

  override def toString: String = Stamp.toString(this)
}

final class Hash(val value: Array[Byte]) extends Stamp {
  override def hashCode: Int = java.util.Arrays.hashCode(value)
}
final class LastModified(val value: Long) extends Stamp {
  override def hashCode: Int = (value ^ (value >>> 32)).toInt
}
final class Exists(val value: Boolean) extends Stamp {
  override def hashCode: Int = if (value) 0 else 1
}

object Stamp {
  implicit val equivStamp: Equiv[Stamp] = new Equiv[Stamp] {
    def equiv(a: Stamp, b: Stamp) = (a, b) match {
      case (h1: Hash, h2: Hash)                   => h1.value sameElements h2.value
      case (e1: Exists, e2: Exists)               => e1.value == e2.value
      case (lm1: LastModified, lm2: LastModified) => lm1.value == lm2.value
      case _                                      => false
    }
  }

  // NOTE: toString/fromString used for serialization, not just for debug prints.

  def toString(s: Stamp): String = s match {
    case e: Exists        => if (e.value) "exists" else "absent"
    case h: Hash          => "hash(" + Hash.toHex(h.value) + ")"
    case lm: LastModified => "lastModified(" + lm.value + ")"
  }

  private val hashPattern = """hash\((\w+)\)""".r
  private val lastModifiedPattern = """lastModified\((\d+)\)""".r

  def fromString(s: String): Stamp = s match {
    case "exists"                   => new Exists(true)
    case "absent"                   => new Exists(false)
    case hashPattern(value)         => new Hash(Hash.fromHex(value))
    case lastModifiedPattern(value) => new LastModified(java.lang.Long.parseLong(value))
    case _                          => throw new IllegalArgumentException("Unrecognized Stamp string representation: " + s)
  }

  def show(s: Stamp): String = s match {
    case h: Hash          => "hash(" + Hash.toHex(h.value) + ")"
    case e: Exists        => if (e.value) "exists" else "does not exist"
    case lm: LastModified => "last modified(" + lm.value + ")"
  }

  val hash = (f: File) => tryStamp(new Hash(Hash(f)))
  val lastModified = (f: File) => tryStamp(new LastModified(f.lastModified))
  val exists = (f: File) => tryStamp(if (f.exists) present else notPresent)

  def tryStamp(g: => Stamp): Stamp = try { g } catch { case i: IOException => notPresent }

  val notPresent = new Exists(false)
  val present = new Exists(true)

  def getStamp(map: Map[File, Stamp], src: File): Stamp = map.getOrElse(src, notPresent)
}

object Stamps {

  /**
    * Creates a ReadStamps instance that will calculate and cache the stamp for sources and binaries
    * on the first request according to the provided `srcStamp` and `binStamp` functions.  Each
    * stamp is calculated separately on demand.
    * The stamp for a product is always recalculated.
    */
  def initial(prodStamp: File => Stamp, srcStamp: File => Stamp, binStamp: File => Stamp): ReadStamps =
    new InitialStamps(prodStamp, srcStamp, binStamp)

  def empty: Stamps = {
    val eSt = Map.empty[File, Stamp]
    apply(eSt, eSt, eSt, Map.empty[File, String])
  }
  def apply(products: Map[File, Stamp],
            sources: Map[File, Stamp],
            binaries: Map[File, Stamp],
            binaryClassNames: Map[File, String]): Stamps =
    new MStamps(products, sources, binaries, binaryClassNames)

  def merge(stamps: Traversable[Stamps]): Stamps = (Stamps.empty /: stamps)(_ ++ _)
}

private class MStamps(val products: Map[File, Stamp],
                      val sources: Map[File, Stamp],
                      val binaries: Map[File, Stamp],
                      val classNames: Map[File, String])
    extends Stamps {
  def allInternalSources: collection.Set[File] = sources.keySet
  def allBinaries: collection.Set[File] = binaries.keySet
  def allProducts: collection.Set[File] = products.keySet

  def ++(o: Stamps): Stamps =
    new MStamps(
      products ++ o.products,
      sources ++ o.sources,
      binaries ++ o.binaries,
      classNames ++ o.classNames
    )

  def markInternalSource(src: File, s: Stamp): Stamps =
    new MStamps(products, sources.updated(src, s), binaries, classNames)

  def markBinary(bin: File, className: String, s: Stamp): Stamps =
    new MStamps(products, sources, binaries.updated(bin, s), classNames.updated(bin, className))

  def markProduct(prod: File, s: Stamp): Stamps =
    new MStamps(products.updated(prod, s), sources, binaries, classNames)

  def filter(prod: File => Boolean, removeSources: Iterable[File], bin: File => Boolean): Stamps =
    new MStamps(
      products.filterKeys(prod),
      sources -- removeSources,
      binaries.filterKeys(bin),
      classNames.filterKeys(bin)
    )

  def groupBy[K](prod: Map[K, File => Boolean], f: File => K, bin: Map[K, File => Boolean]): Map[K, Stamps] = {
    val sourcesMap: Map[K, Map[File, Stamp]] = sources.groupBy(x => f(x._1))

    val constFalse = (f: File) => false
    def kStamps(k: K): Stamps = new MStamps(
      products.filterKeys(prod.getOrElse(k, constFalse)),
      sourcesMap.getOrElse(k, Map.empty[File, Stamp]),
      binaries.filterKeys(bin.getOrElse(k, constFalse)),
      classNames.filterKeys(bin.getOrElse(k, constFalse))
    )

    (for (k <- prod.keySet ++ sourcesMap.keySet ++ bin.keySet) yield (k, kStamps(k))).toMap
  }

  def product(prod: File) = getStamp(products, prod)
  def internalSource(src: File) = getStamp(sources, src)
  def binary(bin: File) = getStamp(binaries, bin)
  def className(bin: File) = classNames get bin

  override def equals(other: Any): Boolean = other match {
    case o: MStamps =>
      products == o.products && sources == o.sources && binaries == o.binaries && classNames == o.classNames
    case _ => false
  }

  override lazy val hashCode: Int = (products :: sources :: binaries :: classNames :: Nil).hashCode

  override def toString: String =
    "Stamps for: %d products, %d sources, %d binaries, %d classNames".format(
      products.size,
      sources.size,
      binaries.size,
      classNames.size
    )
}

private class InitialStamps(prodStamp: File => Stamp, srcStamp: File => Stamp, binStamp: File => Stamp)
    extends ReadStamps {
  import collection.mutable.{ HashMap, Map }
  // cached stamps for files that do not change during compilation
  private val sources: Map[File, Stamp] = new HashMap
  private val binaries: Map[File, Stamp] = new HashMap

  def product(prod: File): Stamp = prodStamp(prod)
  def internalSource(src: File): Stamp = synchronized { sources.getOrElseUpdate(src, srcStamp(src)) }
  def binary(bin: File): Stamp = synchronized { binaries.getOrElseUpdate(bin, binStamp(bin)) }
}
