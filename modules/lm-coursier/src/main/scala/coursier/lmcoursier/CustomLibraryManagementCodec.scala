package coursier.lmcoursier

import sbt.librarymanagement.Resolver
import sjsonnew.JsonFormat

object CustomLibraryManagementCodec extends CoursierLibraryManagementCodec {

  private type ConfFormat = (
    Vector[Resolver],
    Vector[Resolver],
    Boolean,
    Int,
    Int
  )

  private def from(c: CoursierConfiguration): ConfFormat =
    (
      c.resolvers,
      c.otherResolvers,
      c.reorderResolvers,
      c.parallelDownloads,
      c.maxIterations
    )

  private def to(c: ConfFormat): CoursierConfiguration =
    CoursierConfiguration()
      .withResolvers(c._1)
      .withOtherResolvers(c._2)
      .withReorderResolvers(c._3)
      .withParallelDownloads(c._4)
      .withMaxIterations(c._5)

  // Redefine to use a subset of properties, that are serializable
  override implicit lazy val CoursierConfigurationFormat: JsonFormat[CoursierConfiguration] =
    projectFormat[CoursierConfiguration, ConfFormat](from, to)

}
