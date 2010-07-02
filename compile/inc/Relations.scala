/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbti.api.Source
import java.io.File

trait Relations
{
	/** All sources _with at least one product_ . */
	def allSources: collection.Set[File]
	
	def allProducts: collection.Set[File]
	def allBinaryDeps: collection.Set[File]
	def allInternalSrcDeps: collection.Set[File]
	def allExternalDeps: collection.Set[String]
	
	def products(src: File): Set[File]
	def produced(prod: File): Set[File]
	
	def binaryDeps(src: File): Set[File]
	def usesBinary(dep: File): Set[File]
	
	def internalSrcDeps(src: File): Set[File]
	def usesInternalSrc(dep: File): Set[File]
	
	def externalDeps(src: File): Set[String]
	def usesExternal(dep: String): Set[File]
	
	def addProduct(src: File, prod: File): Relations
	def addExternalDep(src: File, dependsOn: String): Relations
	def addInternalSrcDeps(src: File, dependsOn: Iterable[File]): Relations
	def addBinaryDep(src: File, dependsOn: File): Relations
	
	def ++ (o: Relations): Relations
	def -- (sources: Iterable[File]): Relations
	
	def srcProd: Relation[File, File]
	def binaryDep: Relation[File, File]
	def internalSrcDep: Relation[File, File]
	def externalDep: Relation[File, String]
}

object Relations
{
	lazy val e = Relation.empty[File, File]
	def empty: Relations = new MRelations(e, e, e, Relation.empty[File, String])
	def make(srcProd: Relation[File, File], binaryDep: Relation[File, File], internalSrcDep: Relation[File, File], externalDep: Relation[File, String]): Relations =
		new MRelations(srcProd, binaryDep, internalSrcDep, externalDep)
}
/**
* `srcProd` is a relation between a source file and a product: (source, product).
* Note that some source files may not have a product and will not be included in this relation.
*
* `binaryDeps` is a relation between a source file and a binary dependency: (source, binary dependency).
*   This only includes dependencies on classes and jars that do not have a corresponding source/API to track instead.
*   A class or jar with a corresponding source should only be tracked in one of the source dependency relations.
*
* `internalSrcDeps` is a relation between a source file and a source dependency in the same compilation group.
*   Dependencies on sources in other projects belong in external source dependencies.
*
* `externalSrcDeps` is a relation between a source file and a source dependency in another compilation group.
*   Dependencies on sources in the same group belong in internal source dependencies.
*/
private class MRelations(val srcProd: Relation[File, File], val binaryDep: Relation[File, File],
	val internalSrcDep: Relation[File, File], val externalDep: Relation[File, String]) extends Relations
{
	def allSources: collection.Set[File] = srcProd._1s

	def allProducts: collection.Set[File] = srcProd._2s
	def allBinaryDeps: collection.Set[File] = binaryDep._2s
	def allInternalSrcDeps: collection.Set[File] = internalSrcDep._2s
	def allExternalDeps: collection.Set[String] = externalDep._2s
	
	def products(src: File): Set[File] = srcProd.forward(src)
	def produced(prod: File): Set[File] = srcProd.reverse(prod)
	
	def binaryDeps(src: File): Set[File] = binaryDep.forward(src)
	def usesBinary(dep: File): Set[File] = binaryDep.reverse(dep)
	
	def internalSrcDeps(src: File): Set[File] = internalSrcDep.forward(src)
	def usesInternalSrc(dep: File): Set[File] = internalSrcDep.reverse(dep)

	def externalDeps(src: File): Set[String] = externalDep.forward(src)
	def usesExternal(dep: String): Set[File] = externalDep.reverse(dep)

	def addProduct(src: File, prod: File): Relations =
		new MRelations( srcProd + (src, prod), binaryDep, internalSrcDep, externalDep )

	def addExternalDep(src: File, dependsOn: String): Relations =
		new MRelations( srcProd, binaryDep, internalSrcDep, externalDep + (src, dependsOn) )

	def addInternalSrcDeps(src: File, dependsOn: Iterable[File]): Relations =
		new MRelations( srcProd, binaryDep, internalSrcDep + (src, dependsOn ), externalDep )

	def addBinaryDep(src: File, dependsOn: File): Relations =
		new MRelations( srcProd, binaryDep + (src, dependsOn), internalSrcDep, externalDep )
	
	def ++ (o: Relations): Relations =
		new MRelations(srcProd ++ o.srcProd, binaryDep ++ o.binaryDep, internalSrcDep ++ o.internalSrcDep, externalDep ++ o.externalDep)
	def -- (sources: Iterable[File]) =
		new MRelations(srcProd -- sources, binaryDep -- sources, internalSrcDep -- sources, externalDep -- sources)

	override def toString = "Relations:\n  products: " + srcProd + "\n  bin deps: " + binaryDep + "\n  src deps: " +  internalSrcDep + "\n  ext deps: " + externalDep + "\n"
}