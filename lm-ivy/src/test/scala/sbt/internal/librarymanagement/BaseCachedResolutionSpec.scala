package sbt.internal.librarymanagement

import sbt.librarymanagement._
import sbt.librarymanagement.ivy._

trait BaseCachedResolutionSpec extends BaseIvySpecification {
  override def module(
      moduleId: ModuleID,
      deps: Vector[ModuleID],
      scalaFullVersion: Option[String]
  ): ModuleDescriptor = {
    val uo: UpdateOptions = UpdateOptions()
      .withCachedResolution(true)
    module(moduleId, deps, scalaFullVersion, uo, true)
  }
}
