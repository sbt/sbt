/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ModuleInfoFormats { self: sbt.librarymanagement.ScmInfoFormats with sbt.librarymanagement.DeveloperFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val ModuleInfoFormat: JsonFormat[sbt.librarymanagement.ModuleInfo] = new JsonFormat[sbt.librarymanagement.ModuleInfo] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.ModuleInfo = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val nameFormal = unbuilder.readField[String]("nameFormal")
      val description = unbuilder.readField[String]("description")
      val homepage = unbuilder.readField[Option[java.net.URI]]("homepage")
      val startYear = unbuilder.readField[Option[Int]]("startYear")
      val licenses = unbuilder.readField[Vector[scala.Tuple2[String, java.net.URI]]]("licenses")
      val organizationName = unbuilder.readField[String]("organizationName")
      val organizationHomepage = unbuilder.readField[Option[java.net.URI]]("organizationHomepage")
      val scmInfo = unbuilder.readField[Option[sbt.librarymanagement.ScmInfo]]("scmInfo")
      val developers = unbuilder.readField[Vector[sbt.librarymanagement.Developer]]("developers")
      unbuilder.endObject()
      sbt.librarymanagement.ModuleInfo(nameFormal, description, homepage, startYear, licenses, organizationName, organizationHomepage, scmInfo, developers)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.ModuleInfo, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("nameFormal", obj.nameFormal)
    builder.addField("description", obj.description)
    builder.addField("homepage", obj.homepage)
    builder.addField("startYear", obj.startYear)
    builder.addField("licenses", obj.licenses)
    builder.addField("organizationName", obj.organizationName)
    builder.addField("organizationHomepage", obj.organizationHomepage)
    builder.addField("scmInfo", obj.scmInfo)
    builder.addField("developers", obj.developers)
    builder.endObject()
  }
}
}
