package sbt
package internal
package inc

import scala.annotation.tailrec
import xsbti.compile.{ DependencyChanges, IncOptions, IncOptionsUtil, CompileAnalysis }
import xsbti.api.{ Compilation, Source }
import java.io.File

private[inc] abstract class IncrementalCommon(log: sbt.util.Logger, options: IncOptions) {

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
      val wrappedLog = new Incremental.PrefixingLogger("[inv] ")(log)
      def debug(s: => String) = if (options.relationsDebug) wrappedLog.debug(s) else ()
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
      val wrappedLog = new Incremental.PrefixingLogger("[diff] ")(log)
      val apiDiff = new APIDiff
      apiChanges foreach {
        case APIChangeDueToMacroDefinition(src) =>
          wrappedLog.debug(s"Public API is considered to be changed because $src contains a macro definition.")
        case apiChange @ (_: SourceAPIChange[T] | _: NamesChange[T]) =>
          val src = apiChange.modified
          val oldApi = oldAPIMapping(src)
          val newApi = newAPIMapping(src)
          val apiUnifiedPatch = apiDiff.generateApiDiff(src.toString, oldApi.api, newApi.api, contextSize)
          wrappedLog.debug(s"Detected a change in a public API ($src):\n$apiUnifiedPatch")
      }
    } catch {
      case e: ClassNotFoundException =>
        log.error("You have api debugging enabled but DiffUtils library cannot be found on sbt's classpath")
      case e: LinkageError =>
        log.error("Encountered linkage error while trying to load DiffUtils library.")
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
      if (hasMacro && IncOptionsUtil.getRecompileOnMacroDef(options)) {
        Some(APIChangeDueToMacroDefinition(src))
      } else sameAPI(src, a, b)
    }
  }

  protected def sameAPI[T](src: T, a: Source, b: Source): Option[APIChange[T]]

  def shortcutSameSource(a: Source, b: Source): Boolean = a.hash.nonEmpty && b.hash.nonEmpty && sameCompilation(a.compilation, b.compilation) && (a.hash.deep equals b.hash.deep)
  def sameCompilation(a: Compilation, b: Compilation): Boolean = a.startTime == b.startTime && a.outputs.corresponds(b.outputs) {
    case (co1, co2) => co1.sourceDirectory == co2.sourceDirectory && co1.outputDirectory == co2.outputDirectory
  }

  def changedInitial(entry: String => Option[File], sources: Set[File], previousAnalysis0: CompileAnalysis, current: ReadStamps,
    forEntry: File => Option[CompileAnalysis])(implicit equivS: Equiv[Stamp]): InitialChanges =
    {
      val previousAnalysis = previousAnalysis0 match { case a: Analysis => a }
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
    }.toSet

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
          "\nInvalidated products: " + changes.removedProducts +
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

  def externalBinaryModified(entry: String => Option[File], analysis: File => Option[CompileAnalysis], previous: Stamps, current: ReadStamps)(implicit equivS: Equiv[Stamp]): File => Boolean =
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

  def currentExternalAPI(entry: String => Option[File], forEntry: File => Option[CompileAnalysis]): String => Source =
    className =>
      orEmpty(
        for {
          e <- entry(className)
          analysis0 <- forEntry(e)
          analysis = analysis0 match { case a: Analysis => a }
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
}
