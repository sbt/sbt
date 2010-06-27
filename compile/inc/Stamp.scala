/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbti.api.Source
import java.io.{File, IOException}
import Stamp.getStamp

trait ReadStamps
{
	/** The Stamp for the given product at the time represented by this Stamps instance.*/
	def product(prod: File): Stamp
	/** The Stamp for the given source file at the time represented by this Stamps instance.*/
	def internalSource(src: File): Stamp
	/** The Stamp for the given binary dependency at the time represented by this Stamps instance.*/
	def binary(bin: File): Stamp
}

/** Provides information about files as they were at a specific time.*/
trait Stamps extends ReadStamps
{
	def allInternalSources: collection.Set[File]
	def allBinaries: collection.Set[File]
	def allProducts: collection.Set[File]
	
	def sources: Map[File, Stamp]
	def binaries: Map[File, Stamp]
	def products: Map[File, Stamp]
	
	def markInternalSource(src: File, s: Stamp): Stamps
	def markBinary(bin: File, s: Stamp): Stamps
	def markProduct(prod: File, s: Stamp): Stamps
	
	def filter(prod: File => Boolean, removeSources: Iterable[File], bin: File => Boolean): Stamps
	
	def ++ (o: Stamps): Stamps
}

sealed trait Stamp
final class Hash(val value: Array[Byte]) extends Stamp
final class LastModified(val value: Long) extends Stamp
final class Exists(val value: Boolean) extends Stamp

object Stamp
{
	implicit val equivStamp: Equiv[Stamp] = new Equiv[Stamp] {
		def equiv(a: Stamp, b: Stamp) = (a,b) match {
			case (h1: Hash, h2: Hash) => h1.value sameElements h2.value
			case (e1: Exists, e2: Exists) => e1.value == e2.value
			case (lm1: LastModified, lm2: LastModified) => lm1.value == lm2.value
			case _ => false
		}
	}
	
	val hash = (f: File) => tryStamp(new Hash(Hash(f)))
	val lastModified = (f: File) => tryStamp(new LastModified(f.lastModified))
	val exists = (f: File) => tryStamp(if(f.exists) present else notPresent)
	
	def tryStamp(g: => Stamp): Stamp = try { g } catch { case i: IOException => notPresent }
	
	val notPresent = new Exists(false)
	val present = new Exists(true)
	
	def getStamp(map: Map[File, Stamp], src: File): Stamp = map.getOrElse(src, notPresent)
}

object Stamps
{
	def initial(prodStamp: File => Stamp, srcStamp: File => Stamp, binStamp: File => Stamp): ReadStamps = new InitialStamps(prodStamp, srcStamp, binStamp)
	
	def empty: Stamps =
	{
		val eSt = Map.empty[File, Stamp]
		apply(eSt, eSt, eSt)
	}
	def apply(products: Map[File, Stamp], sources: Map[File, Stamp], binaries: Map[File, Stamp]): Stamps = 
		new MStamps(products, sources, binaries)
}

private class MStamps(val products: Map[File, Stamp], val sources: Map[File, Stamp], val binaries: Map[File, Stamp]) extends Stamps
{
	def allInternalSources: collection.Set[File] = sources.keySet
	def allBinaries: collection.Set[File] = binaries.keySet
	def allProducts: collection.Set[File] = products.keySet
	
	def ++ (o: Stamps): Stamps =
		new MStamps(products ++ o.products, sources ++ o.sources, binaries ++ o.binaries)
	
	def markInternalSource(src: File, s: Stamp): Stamps =
		new MStamps(products, sources.updated(src, s), binaries)

	def markBinary(bin: File, s: Stamp): Stamps =
		new MStamps(products, sources, binaries.updated(bin, s))

	def markProduct(prod: File, s: Stamp): Stamps =
		new MStamps(products.updated(prod, s), sources, binaries)
		
	def filter(prod: File => Boolean, removeSources: Iterable[File], bin: File => Boolean): Stamps =
		new MStamps(products.filterKeys(prod), sources -- removeSources, binaries.filterKeys(bin))
	
	def product(prod: File) = getStamp(products, prod)
	def internalSource(src: File) = getStamp(sources, src)
	def binary(bin: File) = getStamp(binaries, bin)
	
}

private class InitialStamps(prodStamp: File => Stamp, srcStamp: File => Stamp, binStamp: File => Stamp) extends ReadStamps
{
	import collection.mutable.{HashMap, Map}
	// cached stamps
	private val products: Map[File, Stamp] = new HashMap
	private val sources: Map[File, Stamp] = new HashMap
	private val binaries: Map[File, Stamp] = new HashMap
	
	def product(prod: File): Stamp = synchronized { products.getOrElseUpdate(prod, prodStamp(prod)) }
	def internalSource(src: File): Stamp = synchronized { sources.getOrElseUpdate(src, srcStamp(src)) }
	def binary(bin: File): Stamp = synchronized { binaries.getOrElseUpdate(bin, binStamp(bin)) }
}