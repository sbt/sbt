package sbt.internal.librarymanagement
package formats

import sjsonnew._
import sbt.librarymanagement._

trait UpdateOptionsFormat { self: BasicJsonProtocol =>

  implicit lazy val UpdateOptionsFormat: JsonFormat[UpdateOptions] =
    project(
      (uo: UpdateOptions) =>
        (
          uo.circularDependencyLevel.name,
          uo.interProjectFirst,
          uo.latestSnapshots,
          uo.consolidatedResolution,
          uo.cachedResolution
      ),
      (xs: (String, Boolean, Boolean, Boolean, Boolean)) =>
        new UpdateOptions(
          levels(xs._1),
          xs._2,
          xs._3,
          xs._4,
          xs._5,
          ConvertResolver.defaultConvert
      )
    )

  private val levels: Map[String, CircularDependencyLevel] = Map(
    "warn" -> CircularDependencyLevel.Warn,
    "ignore" -> CircularDependencyLevel.Ignore,
    "error" -> CircularDependencyLevel.Error
  )
}
