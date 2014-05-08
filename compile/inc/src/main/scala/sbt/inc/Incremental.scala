/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbt.api.{ NameChanges, SameAPI, TopLevel }
import annotation.tailrec
import xsbti.api.{ Compilation, Source }
import xsbti.compile.DependencyChanges
import java.io.File

object Incremental {
  def compile(sources: Set[File],
    entry: String => Option[File],
    previous: Analysis,
    current: ReadStamps,
    forEntry: File => Option[Analysis],
    doCompile: (Set[File], DependencyChanges) => Analysis,
    log: Logger,
    options: IncOptions)(implicit equivS: Equiv[Stamp]): (Boolean, Analysis) =
    {
      val incremental: IncrementalCommon =
        if (options.nameHashing)
          new IncrementalNameHashing(log, options)
        else if (options.antStyle)
          new IncrementalAntStyle(log, options)
        else
          new IncrementalDefaultImpl(log, options)
      val initialChanges = incremental.changedInitial(entry, sources, previous, current, forEntry)
      val binaryChanges = new DependencyChanges {
        val modifiedBinaries = initialChanges.binaryDeps.toArray
        val modifiedClasses = initialChanges.external.allModified.toArray
        def isEmpty = modifiedBinaries.isEmpty && modifiedClasses.isEmpty
      }
      val initialInv = incremental.invalidateInitial(previous.relations, initialChanges)
      log.debug("All initially invalidated sources: " + initialInv + "\n")
      val analysis = manageClassfiles(options) { classfileManager =>
        incremental.cycle(initialInv, sources, binaryChanges, previous, doCompile, classfileManager, 1)
      }
      (!initialInv.isEmpty, analysis)
    }

  // the name of system property that was meant to enable debugging mode of incremental compiler but
  // it ended up being used just to enable debugging of relations. That's why if you migrate to new
  // API for configuring incremental compiler (IncOptions) it's enough to control value of `relationsDebug`
  // flag to achieve the same effect as using `incDebugProp`.
  @deprecated("Use `IncOptions.relationsDebug` flag to enable debugging of relations.", "0.13.2")
  val incDebugProp = "xsbt.inc.debug"

  private[inc] val apiDebugProp = "xsbt.api.debug"
  private[inc] def apiDebug(options: IncOptions): Boolean = options.apiDebug || java.lang.Boolean.getBoolean(apiDebugProp)

  private[sbt] def prune(invalidatedSrcs: Set[File], previous: Analysis): Analysis =
    prune(invalidatedSrcs, previous, ClassfileManager.deleteImmediately())

  private[sbt] def prune(invalidatedSrcs: Set[File], previous: Analysis, classfileManager: ClassfileManager): Analysis =
    {
      classfileManager.delete(invalidatedSrcs.flatMap(previous.relations.products))
      previous -- invalidatedSrcs
    }

  private[this] def manageClassfiles[T](options: IncOptions)(run: ClassfileManager => T): T =
    {
      val classfileManager = options.newClassfileManager()
      val result = try run(classfileManager) catch {
        case e: Exception =>
          classfileManager.complete(success = false)
          throw e
      }
      classfileManager.complete(success = true)
      result
    }

}

private abstract class IncrementalCommon(log: Logger, options: IncOptions) {

  private def incDebug(options: IncOptions): Boolean = options.relationsDebug || java.lang.Boolean.getBoolean(Incremental.incDebugProp)

  // setting the related system property to true will skip checking that the class name
  // still comes from the same classpath entry.  This can workaround bugs in classpath construction,
  // such as the currently problematic -javabootclasspath.  This is subject to removal at any time.
  private[this] def skipClasspathLookup = java.lang.Boolean.getBoolean("xsbt.skip.cp.lookup")

  // TODO: the Analysis for the last successful compilation should get returned + Boolean indicating success
  // TODO: full external name changes, scopeInvalidations
  @tailrec final def cycle(invalidatedRaw: Set[File], allSources: Set[File], binaryChanges: DependencyChanges, previous: Analysis,
    doCompile: (Set[File], DependencyChanges) => Analysis, classfileManager: ClassfileManager, cycleNum: Int): Analysis =
    if (invalidatedRaw.isEmpty)
      previous
    else {
      def debug(s: => String) = if (incDebug(options)) log.debug(s) else ()
      val withPackageObjects = invalidatedRaw ++ invalidatedPackageObjects(invalidatedRaw, previous.relations)
      val invalidated = expand(withPackageObjects, allSources)
      val pruned = Incremental.prune(invalidated, previous, classfileManager)
      debug("********* Pruned: \n" + pruned.relations + "\n*********")

      val fresh = doCompile(invalidated, binaryChanges)
      classfileManager.generated(fresh.relations.allProducts)
      debug("********* Fresh: \n" + fresh.relations + "\n*********")
      val merged = pruned ++ fresh //.copy(relations = pruned.relations ++ fresh.relations, apis = pruned.apis ++ fresh.apis)
      debug("********* Merged: \n" + merged.relations + "\n*********")

      val incChanges = changedIncremental(invalidated, previous.apis.internalAPI _, merged.apis.internalAPI _)
      debug("\nChanges:\n" + incChanges)
      val transitiveStep = options.transitiveStep
      val incInv = invalidateIncremental(merged.relations, merged.apis, incChanges, invalidated, cycleNum >= transitiveStep)
      cycle(incInv, allSources, emptyChanges, merged, doCompile, classfileManager, cycleNum + 1)
    }
  private[this] def emptyChanges: DependencyChanges = new DependencyChanges {
    val modifiedBinaries = new Array[File](0)
    val modifiedClasses = new Array[String](0)
    def isEmpty = true
  }
  private[this] def expand(invalidated: Set[File], all: Set[File]): Set[File] = {
    val recompileAllFraction = options.recompileAllFraction
    if (invalidated.size > all.size * recompileAllFraction) {
      log.debug("Recompiling all " + all.size + " sources: invalidated sources (" + invalidated.size + ") exceeded " + (recompileAllFraction * 100.0) + "% of all sources")
      all ++ invalidated // need the union because all doesn't contain removed sources
    } else invalidated
  }

  protected def invalidatedPackageObjects(invalidated: Set[File], relations: Relations): Set[File]

  /**
   * Logs API changes using debug-level logging. The API are obtained using the APIDiff class.
   *
   * NOTE: This method creates a new APIDiff instance on every invocation.
   */
  private def logApiChanges[T](apiChanges: Iterable[APIChange[T]], oldAPIMapping: T => Source,
    newAPIMapping: T => Source): Unit = {
    val contextSize = options.apiDiffContextSize
    try {
      val apiDiff = new APIDiff
      apiChanges foreach {
        case APIChangeDueToMacroDefinition(src) =>
          log.debug(s"Public API is considered to be changed because $src contains a macro definition.")
        case apiChange @ (_: SourceAPIChange[T] | _: NamesChange[T]) =>
          val src = apiChange.modified
          val oldApi = oldAPIMapping(src)
          val newApi = newAPIMapping(src)
          val apiUnifiedPatch = apiDiff.generateApiDiff(src.toString, oldApi.api, newApi.api, contextSize)
          log.debug(s"Detected a change in a public API (${src.toString}):\n"
            + apiUnifiedPatch)
      }
    } catch {
      case e: ClassNotFoundException =>
        log.error("You have api debugging enabled but DiffUtils library cannot be found on sbt's classpath")
      case e: LinkageError =>
        log.error("Encoutared linkage error while trying to load DiffUtils library.")
        log.trace(e)
      case e: Exception =>
        log.error("An exception has been thrown while trying to dump an api diff.")
        log.trace(e)
    }
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
      val apiChanges = (lastSources, oldApis, newApis).zipped.flatMap { (src, oldApi, newApi) => sameSource(src, oldApi, newApi) }

      if (Incremental.apiDebug(options) && apiChanges.nonEmpty) {
        logApiChanges(apiChanges, oldAPI, newAPI)
      }

      new APIChanges(apiChanges)
    }
  def sameSource[T](src: T, a: Source, b: Source): Option[APIChange[T]] = {
    // Clients of a modified source file (ie, one that doesn't satisfy `shortcutSameSource`) containing macros must be recompiled.
    val hasMacro = a.hasMacro || b.hasMacro
    if (shortcutSameSource(a, b)) {
      None
    } else {
      if (hasMacro && options.recompileOnMacroDef) {
        Some(APIChangeDueToMacroDefinition(src))
      } else sameAPI(src, a, b)
    }
  }

  protected def sameAPI[T](src: T, a: Source, b: Source): Option[APIChange[T]]

  def shortcutSameSource(a: Source, b: Source): Boolean = !a.hash.isEmpty && !b.hash.isEmpty && sameCompilation(a.compilation, b.compilation) && (a.hash.deep equals b.hash.deep)
  def sameCompilation(a: Compilation, b: Compilation): Boolean = a.startTime == b.startTime && a.outputs.corresponds(b.outputs) {
    case (co1, co2) => co1.sourceDirectory == co2.sourceDirectory && co1.outputDirectory == co2.outputDirectory
  }

  def changedInitial(entry: String => Option[File], sources: Set[File], previousAnalysis: Analysis, current: ReadStamps,
    forEntry: File => Option[Analysis])(implicit equivS: Equiv[Stamp]): InitialChanges =
    {
      val previous = previousAnalysis.stamps
      val previousAPIs = previousAnalysis.apis

      val srcChanges = changes(previous.allInternalSources.toSet, sources, f => !equivS.equiv(previous.internalSource(f), current.internalSource(f)))
      val removedProducts = previous.allProducts.filter(p => !equivS.equiv(previous.product(p), current.product(p))).toSet
      val binaryDepChanges = previous.allBinaries.filter(externalBinaryModified(entry, forEntry, previous, current)).toSet
      val extChanges = changedIncremental(previousAPIs.allExternals, previousAPIs.externalAPI _, currentExternalAPI(entry, forEntry))

      InitialChanges(srcChanges, removedProducts, binaryDepChanges, extChanges)
    }

  def changes(previous: Set[File], current: Set[File], existingModified: File => Boolean): Changes[File] =
    new Changes[File] {
      private val inBoth = previous & current
      val removed = previous -- inBoth
      val added = current -- inBoth
      val (changed, unmodified) = inBoth.partition(existingModified)
    }

  def invalidateIncremental(previous: Relations, apis: APIs, changes: APIChanges[File], recompiledSources: Set[File], transitive: Boolean): Set[File] =
    {
      val dependsOnSrc = previous.usesInternalSrc _
      val propagated =
        if (transitive)
          transitiveDependencies(dependsOnSrc, changes.allModified.toSet)
        else
          invalidateIntermediate(previous, changes)

      val dups = invalidateDuplicates(previous)
      if (dups.nonEmpty)
        log.debug("Invalidated due to generated class file collision: " + dups)

      val inv = propagated ++ dups // ++ scopeInvalidations(previous.extAPI _, changes.modified, changes.names)
      val newlyInvalidated = inv -- recompiledSources
      log.debug("All newly invalidated sources after taking into account (previously) recompiled sources:" + newlyInvalidated)
      if (newlyInvalidated.isEmpty) Set.empty else inv
    }

  /** Invalidate all sources that claim to produce the same class file as another source file. */
  def invalidateDuplicates(merged: Relations): Set[File] =
    merged.srcProd.reverseMap.flatMap {
      case (classFile, sources) =>
        if (sources.size > 1) sources else Nil
    } toSet;

  /**
   * Returns the transitive source dependencies of `initial`.
   * Because the intermediate steps do not pull in cycles, this result includes the initial files
   * if they are part of a cycle containing newly invalidated files .
   */
  def transitiveDependencies(dependsOnSrc: File => Set[File], initial: Set[File]): Set[File] =
    {
      val transitiveWithInitial = transitiveDeps(initial)(dependsOnSrc)
      val transitivePartial = includeInitialCond(initial, transitiveWithInitial, dependsOnSrc)
      log.debug("Final step, transitive dependencies:\n\t" + transitivePartial)
      transitivePartial
    }

  /** Invalidates sources based on initially detected 'changes' to the sources, products, and dependencies.*/
  def invalidateInitial(previous: Relations, changes: InitialChanges): Set[File] =
    {
      val srcChanges = changes.internalSrc
      val srcDirect = srcChanges.removed ++ srcChanges.removed.flatMap(previous.usesInternalSrc) ++ srcChanges.added ++ srcChanges.changed
      val byProduct = changes.removedProducts.flatMap(previous.produced)
      val byBinaryDep = changes.binaryDeps.flatMap(previous.usesBinary)
      val byExtSrcDep = invalidateByAllExternal(previous, changes.external) //changes.external.modified.flatMap(previous.usesExternal) // ++ scopeInvalidations
      checkAbsolute(srcChanges.added.toList)
      log.debug(
        "\nInitial source changes: \n\tremoved:" + srcChanges.removed + "\n\tadded: " + srcChanges.added + "\n\tmodified: " + srcChanges.changed +
          "\nRemoved products: " + changes.removedProducts +
          "\nExternal API changes: " + changes.external +
          "\nModified binary dependencies: " + changes.binaryDeps +
          "\nInitial directly invalidated sources: " + srcDirect +
          "\n\nSources indirectly invalidated by:" +
          "\n\tproduct: " + byProduct +
          "\n\tbinary dep: " + byBinaryDep +
          "\n\texternal source: " + byExtSrcDep
      )

      srcDirect ++ byProduct ++ byBinaryDep ++ byExtSrcDep
    }
  private[this] def checkAbsolute(addedSources: List[File]): Unit =
    if (addedSources.nonEmpty) {
      addedSources.filterNot(_.isAbsolute) match {
        case first :: more =>
          val fileStrings = more match {
            case Nil      => first.toString
            case x :: Nil => s"$first and $x"
            case _        => s"$first and ${more.size} others"
          }
          sys.error(s"The incremental compiler requires absolute sources, but some were relative: $fileStrings")
        case Nil =>
      }
    }

  def invalidateByAllExternal(relations: Relations, externalAPIChanges: APIChanges[String]): Set[File] = {
    (externalAPIChanges.apiChanges.flatMap { externalAPIChange =>
      invalidateByExternal(relations, externalAPIChange)
    }).toSet
  }

  /** Sources invalidated by `external` sources in other projects according to the previous `relations`. */
  protected def invalidateByExternal(relations: Relations, externalAPIChange: APIChange[String]): Set[File]

  /** Intermediate invalidation step: steps after the initial invalidation, but before the final transitive invalidation. */
  def invalidateIntermediate(relations: Relations, changes: APIChanges[File]): Set[File] =
    {
      invalidateSources(relations, changes)
    }
  /**
   * Invalidates inheritance dependencies, transitively.  Then, invalidates direct dependencies.  Finally, excludes initial dependencies not
   * included in a cycle with newly invalidated sources.
   */
  private[this] def invalidateSources(relations: Relations, changes: APIChanges[File]): Set[File] =
    {
      val initial = changes.allModified.toSet
      val all = (changes.apiChanges flatMap { change =>
        invalidateSource(relations, change)
      }).toSet
      includeInitialCond(initial, all, allDeps(relations))
    }

  protected def allDeps(relations: Relations): File => Set[File]

  protected def invalidateSource(relations: Relations, change: APIChange[File]): Set[File]

  /**
   * Conditionally include initial sources that are dependencies of newly invalidated sources.
   * * Initial sources included in this step can be because of a cycle, but not always.
   */
  private[this] def includeInitialCond(initial: Set[File], currentInvalidations: Set[File], allDeps: File => Set[File]): Set[File] =
    {
      val newInv = currentInvalidations -- initial
      log.debug("New invalidations:\n\t" + newInv)
      val transitiveOfNew = transitiveDeps(newInv)(allDeps)
      val initialDependsOnNew = transitiveOfNew & initial
      log.debug("Previously invalidated, but (transitively) depend on new invalidations:\n\t" + initialDependsOnNew)
      newInv ++ initialDependsOnNew
    }

  def externalBinaryModified(entry: String => Option[File], analysis: File => Option[Analysis], previous: Stamps, current: ReadStamps)(implicit equivS: Equiv[Stamp]): File => Boolean =
    dependsOn =>
      {
        def inv(reason: String): Boolean = {
          log.debug("Invalidating " + dependsOn + ": " + reason)
          true
        }
        def entryModified(className: String, classpathEntry: File): Boolean =
          {
            val resolved = Locate.resolve(classpathEntry, className)
            if (resolved.getCanonicalPath != dependsOn.getCanonicalPath)
              inv("class " + className + " now provided by " + resolved.getCanonicalPath)
            else
              fileModified(dependsOn, resolved)
          }
        def fileModified(previousFile: File, currentFile: File): Boolean =
          {
            val previousStamp = previous.binary(previousFile)
            val currentStamp = current.binary(currentFile)
            if (equivS.equiv(previousStamp, currentStamp))
              false
            else
              inv("stamp changed from " + previousStamp + " to " + currentStamp)
          }
        def dependencyModified(file: File): Boolean =
          previous.className(file) match {
            case None => inv("no class name was mapped for it.")
            case Some(name) => entry(name) match {
              case None    => inv("could not find class " + name + " on the classpath.")
              case Some(e) => entryModified(name, e)
            }
          }

        analysis(dependsOn).isEmpty &&
          (if (skipClasspathLookup) fileModified(dependsOn, dependsOn) else dependencyModified(dependsOn))

      }

  def currentExternalAPI(entry: String => Option[File], forEntry: File => Option[Analysis]): String => Source =
    className =>
      orEmpty(
        for {
          e <- entry(className)
          analysis <- forEntry(e)
          src <- analysis.relations.definesClass(className).headOption
        } yield analysis.apis.internalAPI(src)
      )

  def orEmpty(o: Option[Source]): Source = o getOrElse APIs.emptySource
  def orTrue(o: Option[Boolean]): Boolean = o getOrElse true

  protected def transitiveDeps[T](nodes: Iterable[T])(dependencies: T => Iterable[T]): Set[T] =
    {
      val xs = new collection.mutable.HashSet[T]
      def all(from: T, tos: Iterable[T]): Unit = tos.foreach(to => visit(from, to))
      def visit(from: T, to: T): Unit =
        if (!xs.contains(to)) {
          log.debug(s"Including $to by $from")
          xs += to
          all(to, dependencies(to))
        }
      log.debug("Initial set of included nodes: " + nodes)
      nodes foreach { start =>
        xs += start
        all(start, dependencies(start))
      }
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

private final class IncrementalDefaultImpl(log: Logger, options: IncOptions) extends IncrementalCommon(log, options) {

  // Package objects are fragile: if they inherit from an invalidated source, get "class file needed by package is missing" error
  //  This might be too conservative: we probably only need package objects for packages of invalidated sources.
  override protected def invalidatedPackageObjects(invalidated: Set[File], relations: Relations): Set[File] =
    invalidated flatMap relations.publicInherited.internal.reverse filter { _.getName == "package.scala" }

  override protected def sameAPI[T](src: T, a: Source, b: Source): Option[SourceAPIChange[T]] = {
    if (SameAPI(a, b))
      None
    else {
      val sourceApiChange = SourceAPIChange(src)
      Some(sourceApiChange)
    }
  }

  /** Invalidates sources based on initially detected 'changes' to the sources, products, and dependencies.*/
  override protected def invalidateByExternal(relations: Relations, externalAPIChange: APIChange[String]): Set[File] = {
    val modified = externalAPIChange.modified
    // Propagate public inheritance dependencies transitively.
    // This differs from normal because we need the initial crossing from externals to sources in this project.
    val externalInheritedR = relations.publicInherited.external
    val byExternalInherited = externalInheritedR.reverse(modified)
    val internalInheritedR = relations.publicInherited.internal
    val transitiveInherited = transitiveDeps(byExternalInherited)(internalInheritedR.reverse _)

    // Get the direct dependencies of all sources transitively invalidated by inheritance
    val directA = transitiveInherited flatMap relations.direct.internal.reverse
    // Get the sources that directly depend on externals.  This includes non-inheritance dependencies and is not transitive.
    val directB = relations.direct.external.reverse(modified)
    transitiveInherited ++ directA ++ directB
  }

  override protected def invalidateSource(relations: Relations, change: APIChange[File]): Set[File] = {
    def reverse(r: Relations.Source) = r.internal.reverse _
    val directDeps: File => Set[File] = reverse(relations.direct)
    val publicInherited: File => Set[File] = reverse(relations.publicInherited)
    log.debug("Invalidating by inheritance (transitively)...")
    val transitiveInherited = transitiveDeps(Set(change.modified))(publicInherited)
    log.debug("Invalidated by transitive public inheritance: " + transitiveInherited)
    val direct = transitiveInherited flatMap directDeps
    log.debug("Invalidated by direct dependency: " + direct)
    transitiveInherited ++ direct
  }

  override protected def allDeps(relations: Relations): File => Set[File] =
    f => relations.direct.internal.reverse(f)

}

/**
 * Implementation of incremental algorithm known as "name hashing". It differs from the default implementation
 * by applying pruning (filter) of member reference dependencies based on used and modified simple names.
 *
 * See MemberReferenceInvalidationStrategy for some more information.
 */
private final class IncrementalNameHashing(log: Logger, options: IncOptions) extends IncrementalCommon(log, options) {

  private val memberRefInvalidator = new MemberRefInvalidator(log)

  // Package objects are fragile: if they inherit from an invalidated source, get "class file needed by package is missing" error
  //  This might be too conservative: we probably only need package objects for packages of invalidated sources.
  override protected def invalidatedPackageObjects(invalidated: Set[File], relations: Relations): Set[File] =
    invalidated flatMap relations.inheritance.internal.reverse filter { _.getName == "package.scala" }

  override protected def sameAPI[T](src: T, a: Source, b: Source): Option[APIChange[T]] = {
    if (SameAPI(a, b))
      None
    else {
      val aNameHashes = a._internalOnly_nameHashes
      val bNameHashes = b._internalOnly_nameHashes
      val modifiedNames = ModifiedNames.compareTwoNameHashes(aNameHashes, bNameHashes)
      val apiChange = NamesChange(src, modifiedNames)
      Some(apiChange)
    }
  }

  /** Invalidates sources based on initially detected 'changes' to the sources, products, and dependencies.*/
  override protected def invalidateByExternal(relations: Relations, externalAPIChange: APIChange[String]): Set[File] = {
    val modified = externalAPIChange.modified
    val invalidationReason = memberRefInvalidator.invalidationReason(externalAPIChange)
    log.debug(s"$invalidationReason\nAll member reference dependencies will be considered within this context.")
    // Propagate inheritance dependencies transitively.
    // This differs from normal because we need the initial crossing from externals to sources in this project.
    val externalInheritanceR = relations.inheritance.external
    val byExternalInheritance = externalInheritanceR.reverse(modified)
    log.debug(s"Files invalidated by inheriting from (external) $modified: $byExternalInheritance; now invalidating by inheritance (internally).")
    val transitiveInheritance = byExternalInheritance flatMap { file =>
      invalidateByInheritance(relations, file)
    }
    val memberRefInvalidationInternal = memberRefInvalidator.get(relations.memberRef.internal,
      relations.names, externalAPIChange)
    val memberRefInvalidationExternal = memberRefInvalidator.get(relations.memberRef.external,
      relations.names, externalAPIChange)

    // Get the member reference dependencies of all sources transitively invalidated by inheritance
    log.debug("Getting direct dependencies of all sources transitively invalidated by inheritance.")
    val memberRefA = transitiveInheritance flatMap memberRefInvalidationInternal
    // Get the sources that depend on externals by member reference.
    // This includes non-inheritance dependencies and is not transitive.
    log.debug(s"Getting sources that directly depend on (external) $modified.")
    val memberRefB = memberRefInvalidationExternal(modified)
    transitiveInheritance ++ memberRefA ++ memberRefB
  }

  private def invalidateByInheritance(relations: Relations, modified: File): Set[File] = {
    val inheritanceDeps = relations.inheritance.internal.reverse _
    log.debug(s"Invalidating (transitively) by inheritance from $modified...")
    val transitiveInheritance = transitiveDeps(Set(modified))(inheritanceDeps)
    log.debug("Invalidated by transitive inheritance dependency: " + transitiveInheritance)
    transitiveInheritance
  }

  override protected def invalidateSource(relations: Relations, change: APIChange[File]): Set[File] = {
    log.debug(s"Invalidating ${change.modified}...")
    val transitiveInheritance = invalidateByInheritance(relations, change.modified)
    val reasonForInvalidation = memberRefInvalidator.invalidationReason(change)
    log.debug(s"$reasonForInvalidation\nAll member reference dependencies will be considered within this context.")
    val memberRefInvalidation = memberRefInvalidator.get(relations.memberRef.internal,
      relations.names, change)
    val memberRef = transitiveInheritance flatMap memberRefInvalidation
    val all = transitiveInheritance ++ memberRef
    all
  }

  override protected def allDeps(relations: Relations): File => Set[File] =
    f => relations.memberRef.internal.reverse(f)

}

private final class IncrementalAntStyle(log: Logger, options: IncOptions) extends IncrementalCommon(log, options) {

  /** Ant-style mode doesn't do anything special with package objects */
  override protected def invalidatedPackageObjects(invalidated: Set[File], relations: Relations): Set[File] = Set.empty

  /** In Ant-style mode we don't need to compare APIs because we don't perform any invalidation */
  override protected def sameAPI[T](src: T, a: Source, b: Source): Option[APIChange[T]] = None

  /** In Ant-style mode we don't perform any invalidation */
  override protected def invalidateByExternal(relations: Relations, externalAPIChange: APIChange[String]): Set[File] = Set.empty

  /** In Ant-style mode we don't perform any invalidation */
  override protected def invalidateSource(relations: Relations, change: APIChange[File]): Set[File] = Set.empty

  /** In Ant-style mode we don't need to perform any dependency analysis hence we can always return an empty set. */
  override protected def allDeps(relations: Relations): File => Set[File] = _ => Set.empty

}
