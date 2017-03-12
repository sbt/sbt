/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbti.api.Source
import xsbti.DependencyContext._
import java.io.File

/**
  * The merge/groupBy functionality requires understanding of the concepts of internalizing/externalizing dependencies:
  *
  * Say we have source files X, Y. And say we have some analysis A_X containing X as a source, and likewise for A_Y and Y.
  * If X depends on Y then A_X contains an external dependency X -> Y.
  *
  * However if we merge A_X and A_Y into a combined analysis A_XY, then A_XY contains X and Y as sources, and therefore
  * X -> Y must be converted to an internal dependency in A_XY. We refer to this as "internalizing" the dependency.
  *
  * The reverse transformation must occur if we group an analysis A_XY into A_X and A_Y, so that the dependency X->Y
  * crosses the boundary. We refer to this as "externalizing" the dependency.
  *
  * These transformations are complicated by the fact that internal dependencies are expressed as source file -> source file,
  * but external dependencies are expressed as source file -> fully-qualified class name.
  */
trait Analysis {
  val stamps: Stamps
  val apis: APIs

  /** Mappings between sources, classes, and binaries. */
  val relations: Relations
  val infos: SourceInfos

  /**
    * Information about compiler runs accumulated since `clean` command has been run.
    *
    * The main use-case for using `compilations` field is to determine how
    * many iterations it took to compilen give code. The `Compilation` object
    * are also stored in `Source` objects so there's an indirect way to recover
    * information about files being recompiled in every iteration.
    *
    * The incremental compilation algorithm doesn't use information stored in
    * `compilations`. It's safe to prune contents of that field without breaking
    * internal consistency of the entire Analysis object.
    */
  val compilations: Compilations

  /** Concatenates Analysis objects naively, i.e., doesn't internalize external deps on added files. See `Analysis.merge`. */
  def ++(other: Analysis): Analysis

  /** Drops all analysis information for `sources` naively, i.e., doesn't externalize internal deps on removed files. */
  def --(sources: Iterable[File]): Analysis

  def copy(stamps: Stamps = stamps,
           apis: APIs = apis,
           relations: Relations = relations,
           infos: SourceInfos = infos,
           compilations: Compilations = compilations): Analysis

  def addSource(src: File,
                api: Source,
                stamp: Stamp,
                info: SourceInfo,
                products: Iterable[(File, String, Stamp)],
                internalDeps: Iterable[InternalDependency],
                externalDeps: Iterable[ExternalDependency],
                binaryDeps: Iterable[(File, String, Stamp)]): Analysis

  @deprecated("Register all products and dependencies in addSource.", "0.13.8")
  def addSource(src: File,
                api: Source,
                stamp: Stamp,
                directInternal: Iterable[File],
                inheritedInternal: Iterable[File],
                info: SourceInfo): Analysis
  @deprecated("Register all products and dependencies in addSource.", "0.13.8")
  def addBinaryDep(src: File, dep: File, className: String, stamp: Stamp): Analysis
  @deprecated("Register all products and dependencies in addSource.", "0.13.8")
  def addExternalDep(src: File, dep: String, api: Source, inherited: Boolean): Analysis
  @deprecated("Register all products and dependencies in addSource.", "0.13.8")
  def addProduct(src: File, product: File, stamp: Stamp, name: String): Analysis

  /** Partitions this Analysis using the discriminator function. Externalizes internal deps that cross partitions. */
  def groupBy[K](discriminator: (File => K)): Map[K, Analysis]

  override lazy val toString = Analysis.summary(this)
}

object Analysis {
  lazy val Empty: Analysis =
    new MAnalysis(Stamps.empty, APIs.empty, Relations.empty, SourceInfos.empty, Compilations.empty)
  private[sbt] def empty(nameHashing: Boolean): Analysis =
    new MAnalysis(
      Stamps.empty,
      APIs.empty,
      Relations.empty(nameHashing = nameHashing),
      SourceInfos.empty,
      Compilations.empty
    )

  /** Merge multiple analysis objects into one. Deps will be internalized as needed. */
  def merge(analyses: Traversable[Analysis]): Analysis = {
    if (analyses.exists(_.relations.nameHashing))
      throw new IllegalArgumentException(
        "Merging of Analyses that have" +
          "`relations.memberRefAndInheritanceDeps` set to `true` is not supported."
      )

    // Merge the Relations, internalizing deps as needed.
    val mergedSrcProd = Relation.merge(analyses map { _.relations.srcProd })
    val mergedBinaryDep = Relation.merge(analyses map { _.relations.binaryDep })
    val mergedClasses = Relation.merge(analyses map { _.relations.classes })

    val stillInternal = Relation.merge(analyses map { _.relations.direct.internal })
    val (internalized, stillExternal) = Relation.merge(analyses map { _.relations.direct.external }) partition {
      case (a, b) => mergedClasses._2s.contains(b)
    }
    val internalizedFiles = Relation.reconstruct(internalized.forwardMap mapValues {
      _ flatMap mergedClasses.reverse
    })
    val mergedInternal = stillInternal ++ internalizedFiles

    val stillInternalPI = Relation.merge(analyses map { _.relations.publicInherited.internal })
    val (internalizedPI, stillExternalPI) = Relation.merge(analyses map {
      _.relations.publicInherited.external
    }) partition { case (a, b) => mergedClasses._2s.contains(b) }
    val internalizedFilesPI = Relation.reconstruct(internalizedPI.forwardMap mapValues {
      _ flatMap mergedClasses.reverse
    })
    val mergedInternalPI = stillInternalPI ++ internalizedFilesPI

    val mergedRelations = Relations.make(
      mergedSrcProd,
      mergedBinaryDep,
      Relations.makeSource(mergedInternal, stillExternal),
      Relations.makeSource(mergedInternalPI, stillExternalPI),
      mergedClasses
    )

    // Merge the APIs, internalizing APIs for targets of dependencies we internalized above.
    val concatenatedAPIs = (APIs.empty /: (analyses map { _.apis }))(_ ++ _)
    val stillInternalAPIs = concatenatedAPIs.internal
    val (internalizedAPIs, stillExternalAPIs) = concatenatedAPIs.external partition { x: (String, Source) =>
      internalized._2s.contains(x._1)
    }
    val internalizedFilesAPIs = internalizedAPIs flatMap {
      case (cls: String, source: Source) =>
        mergedRelations.definesClass(cls) map { file: File =>
          (file, concatenatedAPIs.internalAPI(file))
        }
    }
    val mergedAPIs = APIs(stillInternalAPIs ++ internalizedFilesAPIs, stillExternalAPIs)

    val mergedStamps = Stamps.merge(analyses map { _.stamps })
    val mergedInfos = SourceInfos.merge(analyses map { _.infos })
    val mergedCompilations = Compilations.merge(analyses map { _.compilations })

    new MAnalysis(mergedStamps, mergedAPIs, mergedRelations, mergedInfos, mergedCompilations)
  }

  def summary(a: Analysis): String = {
    val (j, s) = a.apis.allInternalSources.partition(_.getName.endsWith(".java"))
    val c = a.stamps.allProducts
    val ext = a.apis.allExternals
    val jars = a.relations.allBinaryDeps.filter(_.getName.endsWith(".jar"))
    val unreportedCount = a.infos.allInfos.values.map(_.unreportedProblems.size).sum
    val sections =
      counted("Scala source", "", "s", s.size) ++
        counted("Java source", "", "s", j.size) ++
        counted("class", "", "es", c.size) ++
        counted("external source dependenc", "y", "ies", ext.size) ++
        counted("binary dependenc", "y", "ies", jars.size) ++
        counted("unreported warning", "", "s", unreportedCount)
    sections.mkString("Analysis: ", ", ", "")
  }

  def counted(prefix: String, single: String, plural: String, count: Int): Option[String] =
    count match {
      case 0 => None
      case 1 => Some("1 " + prefix + single)
      case x => Some(x.toString + " " + prefix + plural)
    }

}
private class MAnalysis(val stamps: Stamps,
                        val apis: APIs,
                        val relations: Relations,
                        val infos: SourceInfos,
                        val compilations: Compilations)
    extends Analysis {
  def ++(o: Analysis): Analysis =
    new MAnalysis(
      stamps ++ o.stamps,
      apis ++ o.apis,
      relations ++ o.relations,
      infos ++ o.infos,
      compilations ++ o.compilations
    )

  def --(sources: Iterable[File]): Analysis = {
    val newRelations = relations -- sources
    def keep[T](f: (Relations, T) => Set[_]): T => Boolean = f(newRelations, _).nonEmpty

    val newAPIs = apis.removeInternal(sources).filterExt(keep(_ usesExternal _))
    val newStamps = stamps.filter(keep(_ produced _), sources, keep(_ usesBinary _))
    val newInfos = infos -- sources
    new MAnalysis(newStamps, newAPIs, newRelations, newInfos, compilations)
  }

  def copy(stamps: Stamps,
           apis: APIs,
           relations: Relations,
           infos: SourceInfos,
           compilations: Compilations = compilations): Analysis =
    new MAnalysis(stamps, apis, relations, infos, compilations)

  def addSource(src: File,
                api: Source,
                stamp: Stamp,
                info: SourceInfo,
                products: Iterable[(File, String, Stamp)],
                internalDeps: Iterable[InternalDependency],
                externalDeps: Iterable[ExternalDependency],
                binaryDeps: Iterable[(File, String, Stamp)]): Analysis = {

    val newStamps = {
      val productStamps = products.foldLeft(stamps.markInternalSource(src, stamp)) {
        case (tmpStamps, (toProduct, _, prodStamp)) => tmpStamps.markProduct(toProduct, prodStamp)
      }

      binaryDeps.foldLeft(productStamps) {
        case (tmpStamps, (toBinary, className, binStamp)) =>
          tmpStamps.markBinary(toBinary, className, binStamp)
      }
    }

    val newAPIs = externalDeps.foldLeft(apis.markInternalSource(src, api)) {
      case (tmpApis, ExternalDependency(_, toClassName, classApi, _)) =>
        tmpApis.markExternalAPI(toClassName, classApi)
    }

    val newRelations =
      relations.addSource(src, products map (p => (p._1, p._2)), internalDeps, externalDeps, binaryDeps)

    copy(newStamps, newAPIs, newRelations, infos.add(src, info))
  }

  def addSource(src: File,
                api: Source,
                stamp: Stamp,
                directInternal: Iterable[File],
                inheritedInternal: Iterable[File],
                info: SourceInfo): Analysis = {

    val directDeps = directInternal.map(InternalDependency(src, _, DependencyByMemberRef))
    val inheritedDeps = inheritedInternal.map(InternalDependency(src, _, DependencyByInheritance))

    addSource(src, api, stamp, info, products = Nil, directDeps ++ inheritedDeps, Nil, Nil)
  }

  def addBinaryDep(src: File, dep: File, className: String, stamp: Stamp): Analysis =
    copy(
      stamps.markBinary(dep, className, stamp),
      apis,
      relations.addBinaryDeps(src, (dep, className, stamp) :: Nil),
      infos
    )

  def addExternalDep(src: File, dep: String, depAPI: Source, inherited: Boolean): Analysis = {
    val context = if (inherited) DependencyByInheritance else DependencyByMemberRef
    copy(
      stamps,
      apis.markExternalAPI(dep, depAPI),
      relations.addExternalDeps(src, ExternalDependency(src, dep, depAPI, context) :: Nil),
      infos
    )
  }

  def addProduct(src: File, product: File, stamp: Stamp, name: String): Analysis =
    copy(stamps.markProduct(product, stamp), apis, relations.addProducts(src, (product, name) :: Nil), infos)

  def groupBy[K](discriminator: File => K): Map[K, Analysis] = {
    if (relations.nameHashing)
      throw new UnsupportedOperationException(
        "Grouping of Analyses that have" +
          "`relations.memberRefAndInheritanceDeps` set to `true` is not supported."
      )

    def discriminator1(x: (File, _)) = discriminator(x._1) // Apply the discriminator to the first coordinate.

    val kSrcProd = relations.srcProd.groupBy(discriminator1)
    val kBinaryDep = relations.binaryDep.groupBy(discriminator1)
    val kClasses = relations.classes.groupBy(discriminator1)
    val kSourceInfos = infos.allInfos.groupBy(discriminator1)

    val (kStillInternal, kExternalized) = relations.direct.internal partition {
      case (a, b) => discriminator(a) == discriminator(b)
    } match {
      case (i, e) => (i.groupBy(discriminator1), e.groupBy(discriminator1))
    }
    val kStillExternal = relations.direct.external.groupBy(discriminator1)

    // Find all possible groups.
    val allMaps = kSrcProd :: kBinaryDep :: kStillInternal :: kExternalized :: kStillExternal :: kClasses :: kSourceInfos :: Nil
    val allKeys: Set[K] = (Set.empty[K] /: (allMaps map { _.keySet }))(_ ++ _)

    // Map from file to a single representative class defined in that file.
    // This is correct (for now): currently all classes in an external dep share the same Source object,
    // and a change to any of them will act like a change to all of them.
    // We don't use all the top-level classes in source.api.definitions, even though that's more intuitively
    // correct, because this can cause huge bloat of the analysis file.
    def getRepresentativeClass(file: File): Option[String] =
      apis.internalAPI(file).api.definitions.headOption map { _.name }

    // Create an Analysis for each group.
    (for (k <- allKeys) yield {
      def getFrom[A, B](m: Map[K, Relation[A, B]]): Relation[A, B] = m.getOrElse(k, Relation.empty)

      // Products and binary deps.
      val srcProd = getFrom(kSrcProd)
      val binaryDep = getFrom(kBinaryDep)

      // Direct Sources.
      val stillInternal = getFrom(kStillInternal)
      val stillExternal = getFrom(kStillExternal)
      val externalized = getFrom(kExternalized)
      val externalizedClasses =
        Relation.reconstruct(externalized.forwardMap mapValues { _ flatMap getRepresentativeClass })
      val newExternal = stillExternal ++ externalizedClasses

      // Public inherited sources.
      val stillInternalPI = stillInternal filter relations.publicInherited.internal.contains
      val stillExternalPI = stillExternal filter relations.publicInherited.external.contains
      val externalizedPI = externalized filter relations.publicInherited.internal.contains
      val externalizedClassesPI =
        Relation.reconstruct(externalizedPI.forwardMap mapValues { _ flatMap getRepresentativeClass })
      val newExternalPI = stillExternalPI ++ externalizedClassesPI

      // Class names.
      val classes = getFrom(kClasses)

      // Create new relations for this group.
      val newRelations = Relations.make(
        srcProd,
        binaryDep,
        Relations.makeSource(stillInternal, newExternal),
        Relations.makeSource(stillInternalPI, newExternalPI),
        classes
      )

      // Compute new API mappings.
      def apisFor[T](m: Map[T, Source], x: Traversable[T]): Map[T, Source] =
        (x map { e: T =>
          (e, m.get(e))
        } collect { case (t, Some(source)) => (t, source) }).toMap
      val stillInternalAPIs = apisFor(apis.internal, srcProd._1s)
      val stillExternalAPIs = apisFor(apis.external, stillExternal._2s)
      val externalizedAPIs = apisFor(apis.internal, externalized._2s)
      val externalizedClassesAPIs = externalizedAPIs flatMap {
        case (file: File, source: Source) =>
          getRepresentativeClass(file) map { cls: String =>
            (cls, source)
          }
      }
      val newAPIs = APIs(stillInternalAPIs, stillExternalAPIs ++ externalizedClassesAPIs)

      // New stamps.
      val newStamps = Stamps(
        stamps.products.filterKeys(srcProd._2s.contains),
        stamps.sources.filterKeys({ discriminator(_) == k }),
        stamps.binaries.filterKeys(binaryDep._2s.contains),
        stamps.classNames.filterKeys(binaryDep._2s.contains)
      )

      // New infos.
      val newSourceInfos = SourceInfos.make(kSourceInfos.getOrElse(k, Map.empty))

      (k, new MAnalysis(newStamps, newAPIs, newRelations, newSourceInfos, compilations))
    }).toMap
  }

  override def equals(other: Any) = other match {
    // Note: Equality doesn't consider source infos or compilations.
    case o: MAnalysis => stamps == o.stamps && apis == o.apis && relations == o.relations
    case _            => false
  }

  override lazy val hashCode = (stamps :: apis :: relations :: Nil).hashCode
}
