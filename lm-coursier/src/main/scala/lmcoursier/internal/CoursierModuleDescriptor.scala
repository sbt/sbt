package lmcoursier.internal

import lmcoursier.CoursierConfiguration
import sbt.librarymanagement.*

private[lmcoursier] final case class CoursierModuleDescriptor(
    descriptor: ModuleDescriptorConfiguration,
    conf: CoursierConfiguration
) extends ModuleDescriptor {

  def directDependencies: Vector[ModuleID] =
    descriptor.dependencies

  def scalaModuleInfo: Option[ScalaModuleInfo] =
    descriptor.scalaModuleInfo

  def moduleSettings: ModuleDescriptorConfiguration =
    descriptor

  lazy val extraInputHash: Long =
    // Exclude log/logger fields — they contain Logger instances with
    // non-deterministic hashCodes that would break update caching.
    conf.withLog(None).withLogger(None).##
}
