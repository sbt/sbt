package sbt.librarymanagement.coursier

import sbt.librarymanagement._
import sjsonnew.support.murmurhash.Hasher

final case class CoursierModuleDescriptor(
  descriptor: ModuleDescriptorConfiguration,
  conf: CoursierConfiguration
) extends ModuleDescriptor {

  def directDependencies: Vector[ModuleID] =
    descriptor.dependencies

  def scalaModuleInfo: Option[ScalaModuleInfo] =
    descriptor.scalaModuleInfo

  def moduleSettings: CoursierModuleSettings =
    CoursierModuleSettings()

  lazy val extraInputHash: Long = {
    import sbt.librarymanagement.coursier.CustomLibraryManagementCodec._
    Hasher.hash(conf).toOption.fold(0L)(_.toLong)
  }
}
