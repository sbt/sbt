/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbt.api.{NameChanges, SameAPI, TopLevel}
import annotation.tailrec
import xsbti.api.{Compilation, Source}
import xsbti.compile.DependencyChanges
import java.io.File

object Incremental
{
	def compile(sources: Set[File],
		entry: String => Option[File],
		previous: Analysis,
		current: ReadStamps,
		forEntry: File => Option[Analysis],
		doCompile: (Set[File], DependencyChanges) => Analysis,
		log: Logger,
		options: IncOptions)(implicit equivS: Equiv[Stamp]): (Boolean, Analysis) =
	{
		val initialChanges = changedInitial(entry, sources, previous, current, forEntry, options, log)
		val binaryChanges = new DependencyChanges {
			val modifiedBinaries = initialChanges.binaryDeps.toArray
			val modifiedClasses = initialChanges.external.modified.toArray
			def isEmpty = modifiedBinaries.isEmpty && modifiedClasses.isEmpty
		}
		val initialInv = invalidateInitial(previous.relations, initialChanges, log)
		log.debug("All initially invalidated sources: " + initialInv + "\n")
		val analysis = manageClassfiles(options) { classfileManager =>
			cycle(initialInv, sources, binaryChanges, previous, doCompile, classfileManager, 1, log, options)
		}
		(!initialInv.isEmpty, analysis)
	}

	private[this] def manageClassfiles[T](options: IncOptions)(run: ClassfileManager => T): T =
	{
		val classfileManager = options.newClassfileManager()
		val result = try run(classfileManager) catch { case e: Exception =>
			classfileManager.complete(success = false)
			throw e
		}
		classfileManager.complete(success = true)
		result
	}

	val incDebugProp = "xsbt.inc.debug"
	private def incDebug(options: IncOptions): Boolean = options.relationsDebug || java.lang.Boolean.getBoolean(incDebugProp)
	val apiDebugProp = "xsbt.api.debug"
	def apiDebug(options: IncOptions): Boolean =  options.apiDebug || java.lang.Boolean.getBoolean(apiDebugProp)

	// TODO: the Analysis for the last successful compilation should get returned + Boolean indicating success
	// TODO: full external name changes, scopeInvalidations
	@tailrec def cycle(invalidatedRaw: Set[File], allSources: Set[File], binaryChanges: DependencyChanges, previous: Analysis,
		doCompile: (Set[File], DependencyChanges) => Analysis, classfileManager: ClassfileManager, cycleNum: Int, log: Logger, options: IncOptions): Analysis =
		if(invalidatedRaw.isEmpty)
			previous
		else
		{
			def debug(s: => String) = if (incDebug(options)) log.debug(s) else ()
			val withPackageObjects = invalidatedRaw ++ invalidatedPackageObjects(invalidatedRaw, previous.relations)
			val invalidated = expand(withPackageObjects, allSources, log, options)
			val pruned = prune(invalidated, previous, classfileManager)
			debug("********* Pruned: \n" + pruned.relations + "\n*********")

			val fresh = doCompile(invalidated, binaryChanges)
			classfileManager.generated(fresh.relations.allProducts)
			debug("********* Fresh: \n" + fresh.relations + "\n*********")
			val merged = pruned ++ fresh//.copy(relations = pruned.relations ++ fresh.relations, apis = pruned.apis ++ fresh.apis)
			debug("********* Merged: \n" + merged.relations + "\n*********")

			val incChanges = changedIncremental(invalidated, previous.apis.internalAPI _, merged.apis.internalAPI _, options)
			debug("Changes:\n" + incChanges)
			val transitiveStep = options.transitiveStep
			val incInv = invalidateIncremental(merged.relations, incChanges, invalidated, cycleNum >= transitiveStep, log)
			cycle(incInv, allSources, emptyChanges, merged, doCompile, classfileManager, cycleNum+1, log, options)
		}
	private[this] def emptyChanges: DependencyChanges = new DependencyChanges {
		val modifiedBinaries = new Array[File](0)
		val modifiedClasses = new Array[String](0)
		def isEmpty = true
	}
	private[this] def expand(invalidated: Set[File], all: Set[File], log: Logger, options: IncOptions): Set[File] = {
		val recompileAllFraction = options.recompileAllFraction
		if(invalidated.size > all.size * recompileAllFraction) {
			log.debug("Recompiling all " + all.size + " sources: invalidated sources (" + invalidated.size + ") exceeded " + (recompileAllFraction*100.0) + "% of all sources")
			all ++ invalidated // need the union because all doesn't contain removed sources
		}
		else invalidated
	}

	// Package objects are fragile: if they depend on an invalidated source, get "class file needed by package is missing" error
	//  This might be too conservative: we probably only need package objects for packages of invalidated sources.
	private[this] def invalidatedPackageObjects(invalidated: Set[File], relations: Relations): Set[File] =
		invalidated flatMap relations.usesInternalSrc filter { _.getName == "package.scala" }

	/**
	* Accepts the sources that were recompiled during the last step and functions
	* providing the API before and after the last step.  The functions should return
	* an empty API if the file did not/does not exist.
	*/
	def changedIncremental[T](lastSources: collection.Set[T], oldAPI: T => Source, newAPI: T => Source, options: IncOptions): APIChanges[T] =
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

	def shortcutSameSource(a: Source, b: Source): Boolean  =  !a.hash.isEmpty && !b.hash.isEmpty && sameCompilation(a.compilation, b.compilation) && (a.hash.deep equals b.hash.deep)
	def sameCompilation(a: Compilation, b: Compilation): Boolean  =  a.startTime == b.startTime && a.outputs.corresponds(b.outputs){
		case (co1, co2) => co1.sourceDirectory == co2.sourceDirectory && co1.outputDirectory == co2.outputDirectory
  }

	def changedInitial(entry: String => Option[File], sources: Set[File], previousAnalysis: Analysis, current: ReadStamps,
	   forEntry: File => Option[Analysis], options: IncOptions, log: Logger)(implicit equivS: Equiv[Stamp]): InitialChanges =
	{
		val previous = previousAnalysis.stamps
		val previousAPIs = previousAnalysis.apis

		val srcChanges = changes(previous.allInternalSources.toSet, sources,  f => !equivS.equiv( previous.internalSource(f), current.internalSource(f) ) )
		val removedProducts = previous.allProducts.filter( p => !equivS.equiv( previous.product(p), current.product(p) ) ).toSet
		val binaryDepChanges = previous.allBinaries.filter( externalBinaryModified(entry, forEntry, previous, current, log)).toSet
		val extChanges = changedIncremental(previousAPIs.allExternals, previousAPIs.externalAPI _, currentExternalAPI(entry, forEntry), options)

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

	def invalidateIncremental(previous: Relations, changes: APIChanges[File], recompiledSources: Set[File], transitive: Boolean, log: Logger): Set[File] =
	{
		val dependsOnSrc = previous.usesInternalSrc _
		val propagated =
			if(transitive)
				transitiveDependencies(dependsOnSrc, changes.modified, log)
			else
				invalidateStage2(dependsOnSrc, changes.modified, log)

		val dups = invalidateDuplicates(previous)
		if(dups.nonEmpty)
			log.debug("Invalidated due to generated class file collision: " + dups)

		val inv = propagated ++ dups // ++ scopeInvalidations(previous.extAPI _, changes.modified, changes.names)
		val newlyInvalidated = inv -- recompiledSources
		if(newlyInvalidated.isEmpty) Set.empty else inv
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

	/** Invalidates transitive source dependencies including `modified`.*/
	@tailrec def invalidateTransitive(dependsOnSrc: File => Set[File], modified: Set[File], log: Logger): Set[File] =
	{
		val newInv = invalidateDirect(dependsOnSrc, modified)
		log.debug("\tInvalidated direct: " + newInv)
		if(newInv.isEmpty) modified else invalidateTransitive(dependsOnSrc, modified ++ newInv, log)
	}

	/** Returns the transitive source dependencies of `initial`, excluding the files in `initial` in most cases.
	* In three-stage incremental compilation, the `initial` files are the sources from step 2 that had API changes.
	* Because strongly connected components (cycles) are included in step 2, the files with API changes shouldn't
	* need to be compiled in step 3 if their dependencies haven't changed.  If there are new cycles introduced after
	* step 2, these can require step 2 sources to be included in step 3 recompilation.
	*/
	def transitiveDependencies(dependsOnSrc: File => Set[File], initial: Set[File], log: Logger): Set[File] =
	{
		// include any file that depends on included files
		def recheck(included: Set[File], process: Set[File], excluded: Set[File]): Set[File] =
		{
			val newIncludes = (process flatMap dependsOnSrc) intersect excluded
			if(newIncludes.isEmpty)
				included
			else
				recheck(included ++ newIncludes, newIncludes, excluded -- newIncludes)
		}
		val transitiveOnly = transitiveDepsOnly(initial)(dependsOnSrc)
		log.debug("Step 3 transitive dependencies:\n\t" + transitiveOnly)
		val stage3 = recheck(transitiveOnly, transitiveOnly, initial)
		log.debug("Step 3 sources from new step 2 source dependencies:\n\t" + (stage3 -- transitiveOnly))
		stage3
	}


	def invalidateStage2(dependsOnSrc: File => Set[File], initial: Set[File], log: Logger): Set[File] =
	{
		val initAndImmediate = initial ++ initial.flatMap(dependsOnSrc)
		log.debug("Step 2 changed sources and immdediate dependencies:\n\t" + initAndImmediate)
		val components = sbt.inc.StronglyConnected(initAndImmediate)(dependsOnSrc)
		log.debug("Non-trivial strongly connected components: " + components.filter(_.size > 1).mkString("\n\t", "\n\t", ""))
		val inv = components.filter(initAndImmediate.exists).flatten
		log.debug("Step 2 invalidated sources:\n\t" + inv)
		inv
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
		prune(invalidatedSrcs, previous, ClassfileManager.deleteImmediately())

	def prune(invalidatedSrcs: Set[File], previous: Analysis, classfileManager: ClassfileManager): Analysis =
	{
		classfileManager.delete( invalidatedSrcs.flatMap(previous.relations.products) )
		previous -- invalidatedSrcs
	}

	def externalBinaryModified(entry: String => Option[File], analysis: File => Option[Analysis], previous: Stamps, current: ReadStamps, log: Logger)(implicit equivS: Equiv[Stamp]): File => Boolean =
		dependsOn =>
		{
			def inv(reason: String): Boolean = {
				log.debug("Invalidating " + dependsOn.getCanonicalPath + ": " + reason)
				true
			}
			def entryModified(className: String, classpathEntry: File): Boolean =
			{
				val resolved = Locate.resolve(classpathEntry, className)
				if(resolved.getCanonicalPath != dependsOn.getCanonicalPath)
					inv("class " + className + " now provided by " + resolved.getCanonicalPath)
				else {
					val previousStamp = previous.binary(dependsOn)
					val resolvedStamp = current.binary(resolved)
					if(equivS.equiv(previousStamp, resolvedStamp))
						false
					else
						inv("stamp changed from " + previousStamp + " to " + resolvedStamp)
				}
			}

			analysis(dependsOn).isEmpty && {
				previous.className(dependsOn) match {
					case None => inv("no class name was mapped for it.")
					case Some(name) => entry(name) match {
						case None => inv("could not find class " + name + " on the classpath.")
						case Some(e) => entryModified(name, e)
					}
				}
			}
		}

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

	private[this] def transitiveDepsOnly[T](nodes: Iterable[T])(dependencies: T => Iterable[T]): Set[T] =
	{
		val xs = new collection.mutable.HashSet[T]
		def all(ns: Iterable[T]): Unit = ns.foreach(visit)
		def visit(n: T): Unit =
			if (!xs.contains(n)) {
				xs += n
				all(dependencies(n))
			}
		all(nodes)
		xs --= nodes
		xs.toSet
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
