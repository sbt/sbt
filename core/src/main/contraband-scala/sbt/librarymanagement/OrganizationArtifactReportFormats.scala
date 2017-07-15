/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait OrganizationArtifactReportFormats { self: sbt.librarymanagement.ModuleReportFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val OrganizationArtifactReportFormat: JsonFormat[sbt.librarymanagement.OrganizationArtifactReport] = new JsonFormat[sbt.librarymanagement.OrganizationArtifactReport] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.OrganizationArtifactReport = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val organization = unbuilder.readField[String]("organization")
      val name = unbuilder.readField[String]("name")
      val modules = unbuilder.readField[Vector[sbt.librarymanagement.ModuleReport]]("modules")
      unbuilder.endObject()
      sbt.librarymanagement.OrganizationArtifactReport(organization, name, modules)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.OrganizationArtifactReport, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("organization", obj.organization)
    builder.addField("name", obj.name)
    builder.addField("modules", obj.modules)
    builder.endObject()
  }
}
}
