/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ModuleConfigurationFormats { self: sbt.librarymanagement.ResolverFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val ModuleConfigurationFormat: JsonFormat[sbt.librarymanagement.ModuleConfiguration] = new JsonFormat[sbt.librarymanagement.ModuleConfiguration] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.ModuleConfiguration = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val organization = unbuilder.readField[String]("organization")
      val name = unbuilder.readField[String]("name")
      val revision = unbuilder.readField[String]("revision")
      val resolver = unbuilder.readField[sbt.librarymanagement.Resolver]("resolver")
      unbuilder.endObject()
      sbt.librarymanagement.ModuleConfiguration(organization, name, revision, resolver)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.ModuleConfiguration, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("organization", obj.organization)
    builder.addField("name", obj.name)
    builder.addField("revision", obj.revision)
    builder.addField("resolver", obj.resolver)
    builder.endObject()
  }
}
}
