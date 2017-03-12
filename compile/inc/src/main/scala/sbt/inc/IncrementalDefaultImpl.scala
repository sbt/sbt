package sbt
package inc

import xsbti.api.Source
import xsbt.api.SameAPI
import java.io.File

private final class IncrementalDefaultImpl(log: Logger, options: IncOptions)
    extends IncrementalCommon(log, options) {

  // Package objects are fragile: if they inherit from an invalidated source, get "class file needed by package is missing" error
  //  This might be too conservative: we probably only need package objects for packages of invalidated sources.
  override protected def invalidatedPackageObjects(invalidated: Set[File],
                                                   relations: Relations,
                                                   apis: APIs): Set[File] =
    invalidated flatMap relations.publicInherited.internal.reverse filter apis.hasPackageObject

  override protected def sameAPI[T](src: T, a: Source, b: Source): Option[SourceAPIChange[T]] =
    if (SameAPI(a, b))
      None
    else {
      val sourceApiChange = SourceAPIChange(src)
      Some(sourceApiChange)
    }

  /** Invalidates sources based on initially detected 'changes' to the sources, products, and dependencies.*/
  override protected def invalidateByExternal(relations: Relations,
                                              externalAPIChange: APIChange[String]): Set[File] = {
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
