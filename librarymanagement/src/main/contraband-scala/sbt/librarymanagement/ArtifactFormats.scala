/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ArtifactFormats { self: sbt.librarymanagement.ConfigurationFormats with sbt.librarymanagement.ChecksumFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val ArtifactFormat: JsonFormat[sbt.librarymanagement.Artifact] = new JsonFormat[sbt.librarymanagement.Artifact] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.Artifact = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val name = unbuilder.readField[String]("name")
      val `type` = unbuilder.readField[String]("type")
      val extension = unbuilder.readField[String]("extension")
      val classifier = unbuilder.readField[Option[String]]("classifier")
      val configurations = unbuilder.readField[Vector[sbt.librarymanagement.Configuration]]("configurations")
      val url = unbuilder.readField[Option[java.net.URL]]("url")
      val extraAttributes = unbuilder.readField[Map[String, String]]("extraAttributes")
      val checksum = unbuilder.readField[Option[sbt.librarymanagement.Checksum]]("checksum")
      unbuilder.endObject()
      sbt.librarymanagement.Artifact(name, `type`, extension, classifier, configurations, url, extraAttributes, checksum)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.Artifact, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("type", obj.`type`)
    builder.addField("extension", obj.extension)
    builder.addField("classifier", obj.classifier)
    builder.addField("configurations", obj.configurations)
    builder.addField("url", obj.url)
    builder.addField("extraAttributes", obj.extraAttributes)
    builder.addField("checksum", obj.checksum)
    builder.endObject()
  }
}
}
