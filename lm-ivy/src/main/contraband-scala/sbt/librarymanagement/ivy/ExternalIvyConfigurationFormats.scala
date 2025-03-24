/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement.ivy
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ExternalIvyConfigurationFormats { self: sbt.internal.librarymanagement.formats.GlobalLockFormat & sbt.internal.librarymanagement.formats.LoggerFormat & sbt.librarymanagement.ivy.formats.UpdateOptionsFormat & sbt.librarymanagement.ResolverFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val ExternalIvyConfigurationFormat: JsonFormat[sbt.librarymanagement.ivy.ExternalIvyConfiguration] = new JsonFormat[sbt.librarymanagement.ivy.ExternalIvyConfiguration] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.ivy.ExternalIvyConfiguration = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val lock = unbuilder.readField[Option[xsbti.GlobalLock]]("lock")
      val log = unbuilder.readField[Option[xsbti.Logger]]("log")
      val updateOptions = unbuilder.readField[sbt.librarymanagement.ivy.UpdateOptions]("updateOptions")
      val baseDirectory = unbuilder.readField[Option[java.io.File]]("baseDirectory")
      val uri = unbuilder.readField[Option[java.net.URI]]("uri")
      val extraResolvers = unbuilder.readField[Vector[sbt.librarymanagement.Resolver]]("extraResolvers")
      unbuilder.endObject()
      sbt.librarymanagement.ivy.ExternalIvyConfiguration(lock, log, updateOptions, baseDirectory, uri, extraResolvers)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.ivy.ExternalIvyConfiguration, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("lock", obj.lock)
    builder.addField("log", obj.log)
    builder.addField("updateOptions", obj.updateOptions)
    builder.addField("baseDirectory", obj.baseDirectory)
    builder.addField("uri", obj.uri)
    builder.addField("extraResolvers", obj.extraResolvers)
    builder.endObject()
  }
}
}
