/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbt.api.{NameChanges, SameAPI, TopLevel}
import annotation.tailrec
import xsbti.api.{Compilation, Source}
import java.io.File

object Incremental
{
	def debug(s: => String) = if(java.lang.Boolean.getBoolean("xsbt.inc.debug")) println(s) else ()
	def compile(sources: Set[File], entry: String => Option[File], previous: Analysis, current: ReadStamps, forEntry: File => Option[Analysis], doCompile: Set[File] => Analysis, log: Logger)(implicit equivS: Equiv[Stamp]): (Boolean, Analysis) =
	{
		val initialChanges = changedInitial(entry, sources, previous, current, forEntry)
		val initialInv = invalidateInitial(previous.relations, initialChanges, log)
		log.debug("Initially invalidated: " + initialInv)
		val analysis = cycle(initialInv, previous, doCompile, log)
		(!initialInv.isEmpty, analysis)
	}

	// TODO: the Analysis for the last successful compilation should get returned + Boolean indicating success
	// TODO: full external name changes, scopeInvalidations
	def cycle(invalidated: Set[File], previous: Analysis, doCompile: Set[File] => Analysis, log: Logger): Analysis =
		if(invalidated.isEmpty)
			previous
		else
		{
			val pruned = prune(invalidated, previous)
			debug("********* Pruned: \n" + pruned.relations + "\n*********")
			val fresh = doCompile(invalidated)
			debug("********* Fresh: \n" + fresh.relations + "\n*********")
			val merged = pruned ++ fresh//.copy(relations = pruned.relations ++ fresh.relations, apis = pruned.apis ++ fresh.apis)
			debug("********* Merged: \n" + merged.relations + "\n*********")
			val incChanges = changedIncremental(invalidated, previous.apis.internalAPI _, merged.apis.internalAPI _)
			debug("Changes:\n" + incChanges)
			val incInv = invalidateIncremental(merged.relations, incChanges, invalidated, log)
			log.debug("Incrementally invalidated: " + incInv)
			cycle(incInv, merged, doCompile, log)
		}
	

	/**
	* Accepts the sources that were recompiled during the last step and functions
	* providing the API before and after the last step.  The functions should return
	* an empty API if the file did not/does not exist.
	*/
	def changedIncremental[T](lastSources: collection.Set[T], oldAPI: T => Source, newAPI: T => Source): APIChanges[T] =
	{
		val oldApis = lastSources.toSeq map oldAPI
		val newApis = lastSources.toSeq map newAPI
		val changes = (lastSources, oldApis, newApis).zipped.filter { (src, oldApi, newApi) => !sameSource(oldApi, newApi) }

		val changedNames = TopLevel.nameChanges(changes._3, changes._2 )

		val modifiedAPIs = changes._1.toSet

		new APIChanges(modifiedAPIs, changedNames)
	}
	def sameSource(a: Source, b: Source): Boolean = {
		// Clients of a modified source file (ie, one that doesn't satisfy `shortcutSameSource`) containing macros must be recompiled.
		val hasMacro = a.hasMacro || b.hasMacro
		shortcutSameSource(a, b) || (!hasMacro && SameAPI(a,b))
	}

	def shortcutSameSource(a: Source, b: Source): Boolean  =  !a.hash.isEmpty && !b.hash.isEmpty && sameCompilation(a.compilation, b.compilation) && (a.hash deepEquals b.hash)
	def sameCompilation(a: Compilation, b: Compilation): Boolean  =  a.startTime == b.startTime && a.target == b.target

	def changedInitial(entry: String => Option[File], sources: Set[File], previousAnalysis: Analysis, current: ReadStamps, forEntry: File => Option[Analysis])(implicit equivS: Equiv[Stamp]): InitialChanges =
	{
		val previous = previousAnalysis.stamps
		val previousAPIs = previousAnalysis.apis
		
		val srcChanges = changes(previous.allInternalSources.toSet, sources,  f => !equivS.equiv( previous.internalSource(f), current.internalSource(f) ) )
		val removedProducts = previous.allProducts.filter( p => !equivS.equiv( previous.product(p), current.product(p) ) ).toSet
		val binaryDepChanges = previous.allBinaries.filter( externalBinaryModified(entry, forEntry, previous, current)).toSet
		val extChanges = changedIncremental(previousAPIs.allExternals, previousAPIs.externalAPI _, currentExternalAPI(entry, forEntry))

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

	def invalidateIncremental(previous: Relations, changes: APIChanges[File], recompiledSources: Set[File], log: Logger): Set[File] =
	{
		val inv =
			invalidateTransitive(previous.usesInternalSrc _,  changes.modified, log) ++
			invalidateDuplicates(previous)
			// ++ scopeInvalidations(previous.extAPI _, changes.modified, changes.names)
		if((inv -- recompiledSources).isEmpty) Set.empty else inv
	}

	/** Invalidate all sources that claim to produce the same class file as another source file. */
	def invalidateDuplicates(merged: Relations): Set[File] =
		merged.srcProd.reverseMap.flatMap { case (classFile, sources) =>
			if(sources.size > 1) sources else Nil
		} toSet;

	/** Only invalidates direct source dependencies.  It excludes any sources that were recompiled during the previous run.
	* Callers may want to augment the returned set with 'modified' or all sources recompiled up to this point. */
	def invalidateDirect(dependsOnSrc: File => Set[File], modified: Set[File]): Set[File] =
		(modified flatMap dependsOnSrc) -- modified

	/** Invalidates transitive source dependencies including `modified`.  It excludes any sources that were recompiled during the previous run.*/
	@tailrec def invalidateTransitive(dependsOnSrc: File => Set[File], modified: Set[File], log: Logger): Set[File] =
	{
		val newInv = invalidateDirect(dependsOnSrc, modified)
		log.debug("\tInvalidated direct: " + newInv)
		if(newInv.isEmpty) modified else invalidateTransitive(dependsOnSrc, modified ++ newInv, log)
	}

	/** Invalidates sources based on initially detected 'changes' to the sources, products, and dependencies.*/
	def invalidateInitial(previous: Relations, changes: InitialChanges, log: Logger): Set[File] =
	{
		val srcChanges = changes.internalSrc
		val srcDirect = srcChanges.removed ++ srcChanges.removed.flatMap(previous.usesInternalSrc) ++ srcChanges.added ++ srcChanges.changed
		val byProduct = changes.removedProducts.flatMap(previous.produced)
		val byBinaryDep = changes.binaryDeps.flatMap(previous.usesBinary)
		val byExtSrcDep = changes.external.modified.flatMap(previous.usesExternal) // ++ scopeInvalidations
		log.debug(
			"\nInitial source changes: \n\tremoved:" + srcChanges.removed + "\n\tadded: " + srcChanges.added + "\n\tmodified: " + srcChanges.changed +
			"\nRemoved products: " + changes.removedProducts +
			"\nModified external sources: " + changes.external.modified +
			"\nModified binary dependencies: " + changes.binaryDeps +
			"\nInitial directly invalidated sources: " + srcDirect +
			"\n\nSources indirectly invalidated by:" +
			"\n\tproduct: " + byProduct +
			"\n\tbinary dep: " + byBinaryDep +
			"\n\texternal source: " + byExtSrcDep
		)

		srcDirect ++ byProduct ++ byBinaryDep ++ byExtSrcDep
	}

	def prune(invalidatedSrcs: Set[File], previous: Analysis): Analysis =
	{
		IO.deleteFilesEmptyDirs( invalidatedSrcs.flatMap(previous.relations.products) )
		previous -- invalidatedSrcs
	}

	def externalBinaryModified(entry: String => Option[File], analysis: File => Option[Analysis], previous: Stamps, current: ReadStamps)(implicit equivS: Equiv[Stamp]): File => Boolean =
		dependsOn =>
			analysis(dependsOn).isEmpty &&
			orTrue(
				for {
					name <- previous.className(dependsOn)
					e <- entry(name)
				} yield {
					val resolved = Locate.resolve(e, name)
					(resolved != dependsOn) || !equivS.equiv(previous.binary(dependsOn), current.binary(resolved))
				}
			)

	def currentExternalAPI(entry: String => Option[File], forEntry: File => Option[Analysis]): String => Source =
		className =>
			orEmpty(
				for {
					e <- entry(className)
					analysis <- forEntry(e)
					src <- analysis.relations.definesClass(className).headOption
				} yield
					analysis.apis.internalAPI(src)
			)

	def orEmpty(o: Option[Source]): Source = o getOrElse APIs.emptySource
	def orTrue(o: Option[Boolean]): Boolean = o getOrElse true
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
