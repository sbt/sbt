/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbt.api.{NameChanges, SameAPI, TopLevel}
import annotation.tailrec
import xsbti.api.Source
import java.io.File

object Incremental
{
	// TODO: the Analysis for the last successful compilation should get returned + Boolean indicating success
	// TODO: full external name changes, scopeInvalidations
	def compile(sources: Set[File], previous: Analysis, current: ReadStamps, externalAPI: String => Source, doCompile: Set[File] => Analysis)(implicit equivS: Equiv[Stamp]): Analysis =
	{
		def cycle(invalidated: Set[File], previous: Analysis): Analysis =
			if(invalidated.isEmpty)
				previous
			else
			{
				val pruned = prune(invalidated, previous)
				val fresh = doCompile(invalidated)
				val merged = pruned ++ fresh//.copy(relations = pruned.relations ++ fresh.relations, apis = pruned.apis ++ fresh.apis)
				val incChanges = changedIncremental(invalidated, previous.apis.internalAPI _, merged.apis.internalAPI _)
				val incInv = invalidateIncremental(merged.relations, incChanges)
				cycle(incInv, merged)
			}

		val initialChanges = changedInitial(sources, previous.stamps, previous.apis, current, externalAPI)
		val initialInv = invalidateInitial(previous.relations, initialChanges)
		cycle(initialInv, previous)
	}
	

	/**
	* Accepts the sources that were recompiled during the last step and functions
	* providing the API before and after the last step.  The functions should return
	* an empty API if the file did not/does not exist.
	*/
	def changedIncremental[T](lastSources: collection.Set[T], oldAPI: T => Source, newAPI: T => Source): APIChanges[T] =
	{
		val oldApis = lastSources map oldAPI
		val newApis = lastSources map newAPI

		val changes = (lastSources, oldApis, newApis).zipped.filter { (src, oldApi, newApi) => SameAPI(oldApi, newApi) }

		val changedNames = TopLevel.nameChanges(changes._3, changes._2 )

		val modifiedAPIs = changes._1.toSet

		APIChanges(modifiedAPIs, changedNames)
	}

	def changedInitial(sources: Set[File], previous: Stamps, previousAPIs: APIs, current: ReadStamps, externalAPI: String => Source)(implicit equivS: Equiv[Stamp]): InitialChanges =
	{
		val srcChanges = changes(previous.allInternalSources.toSet, sources,  f => !equivS.equiv( previous.internalSource(f), current.internalSource(f) ) )
		val removedProducts = previous.allProducts.filter( p => !equivS.equiv( previous.product(p), current.product(p) ) ).toSet
		val binaryDepChanges = previous.allBinaries.filter( f => !equivS.equiv( previous.binary(f), current.binary(f) ) ).toSet
		val extChanges = changedIncremental(previousAPIs.allExternals, previousAPIs.externalAPI, externalAPI)

		InitialChanges(srcChanges, removedProducts, binaryDepChanges, extChanges )
	}

	def changes(previous: Set[File], current: Set[File], existingModified: File => Boolean): Changes[File] =
		new Changes[File]
		{
			private val inBoth = previous & current
			val removed = previous -- inBoth
			val added = current -- inBoth
			val (changed, unmodified) = inBoth.partition(existingModified)
		}

	def invalidateIncremental(previous: Relations, changes: APIChanges[File]): Set[File] =
		invalidateTransitive(previous.internalSrcDeps _,  changes.modified )// ++ scopeInvalidations(previous.extAPI _, changes.modified, changes.names)

	/** Only invalidates direct source dependencies.  It excludes any sources that were recompiled during the previous run.
	* Callers may want to augment the returned set with 'modified' or even all sources recompiled up to this point. */
	def invalidateDirect(sourceDeps: File => Set[File], modified: Set[File]): Set[File] =
		(modified flatMap sourceDeps) -- modified

	/** Invalidates transitive source dependencies including `modified`.  It excludes any sources that were recompiled during the previous run.*/
	@tailrec def invalidateTransitive(sourceDeps: File => Set[File], modified: Set[File]): Set[File] =
	{
		val newInv = invalidateDirect(sourceDeps, modified)
		if(newInv.isEmpty) modified else invalidateTransitive(sourceDeps, modified ++ newInv)
	}

	/** Invalidates sources based on initially detected 'changes' to the sources, products, and dependencies.*/
	def invalidateInitial(previous: Relations, changes: InitialChanges): Set[File] =
	{
		val srcChanges = changes.internalSrc
		val srcDirect = srcChanges.removed.flatMap(previous.usesInternalSrc) ++ srcChanges.added ++ srcChanges.changed
		val byProduct = changes.removedProducts.flatMap(previous.produced)
		val byBinaryDep = changes.binaryDeps.flatMap(previous.usesBinary)
		val byExtSrcDep = changes.external.modified.flatMap(previous.usesExternal) // ++ scopeInvalidations

		srcDirect ++ byProduct ++ byBinaryDep ++ byExtSrcDep
	}

	def prune(invalidatedSrcs: Set[File], previous: Analysis): Analysis =
	{
		IO.delete( invalidatedSrcs.flatMap(previous.relations.products) )
		previous -- invalidatedSrcs
	}

	// unmodifiedSources should not contain any sources in the previous compilation run
	//  (this may unnecessarily invalidate them otherwise)
	/*def scopeInvalidation(previous: Analysis, otherSources: Set[File], names: NameChanges): Set[File] =
	{
		val newNames = newTypes ++ names.newTerms
		val newMap = pkgNameMap(newNames)
		otherSources filter { src => scopeAffected(previous.extAPI(src), previous.srcDependencies(src), newNames, newMap) }
	}

	def scopeAffected(api: Source, srcDependencies: Iterable[Source], newNames: Set[String], newMap: Map[String, List[String]]): Boolean =
		collisions_?(TopLevel.names(api.definitions), newNames) ||
			pkgs(api) exists {p => shadowed_?(p, srcDependencies, newMap) }

	def collisions_?(existing: Set[String], newNames: Map[String, List[String]]): Boolean =
		!(existing ** newNames).isEmpty

	// A proper implementation requires the actual symbol names used.  This is a crude approximation in the meantime.
	def shadowed_?(fromPkg: List[String], srcDependencies: Iterable[Source], newNames: Map[String, List[String]]): Boolean =
	{
		lazy val newPN = newNames.filter { pn => properSubPkg(fromPkg, pn._2) }

		def isShadowed(usedName: String): Boolean =
		{
			val (usedPkg, name) = pkgAndName(usedName)
			newPN.get(name).forall { nPkg => properSubPkg(usedPkg, nPkg) }
		}

		val usedNames = TopLevel.names(srcDependencies) // conservative approximation of referenced top-level names
		usedNames exists isShadowed
	}
	def pkgNameMap(names: Iterable[String]): Map[String, List[String]] =
		(names map pkgAndName).toMap
	def pkgAndName(s: String) =
	{
		val period = s.lastIndexOf('.')
		if(period < 0) (Nil, s) else (s.substring(0, period).split("\\."), s.substring(period+1))
	}
	def pkg(s: String) = pkgAndName(s)._1
	def properSubPkg(testParent: Seq[String], testSub: Seq[String]) = testParent.length < testSub.length && testSub.startsWith(testParent)
	def pkgs(api: Source) = names(api :: Nil).map(pkg)*/
}
