/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ModuleIDFormats { self: sbt.librarymanagement.ArtifactFormats with sbt.librarymanagement.ConfigRefFormats with sbt.librarymanagement.ChecksumFormats with sjsonnew.BasicJsonProtocol with sbt.librarymanagement.InclExclRuleFormats with sbt.librarymanagement.CrossVersionFormats with sbt.librarymanagement.DisabledFormats with sbt.librarymanagement.BinaryFormats with sbt.librarymanagement.ConstantFormats with sbt.librarymanagement.PatchFormats with sbt.librarymanagement.FullFormats with sbt.librarymanagement.For3Use2_13Formats with sbt.librarymanagement.For2_13Use3Formats =>
implicit lazy val ModuleIDFormat: JsonFormat[sbt.librarymanagement.ModuleID] = new JsonFormat[sbt.librarymanagement.ModuleID] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.ModuleID = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val organization = unbuilder.readField[String]("organization")
      val name = unbuilder.readField[String]("name")
      val revision = unbuilder.readField[String]("revision")
      val configurations = unbuilder.readField[Option[String]]("configurations")
      val isChanging = unbuilder.readField[Boolean]("isChanging")
      val isTransitive = unbuilder.readField[Boolean]("isTransitive")
      val isForce = unbuilder.readField[Boolean]("isForce")
      val explicitArtifacts = unbuilder.readField[Vector[sbt.librarymanagement.Artifact]]("explicitArtifacts")
      val inclusions = unbuilder.readField[Vector[sbt.librarymanagement.InclExclRule]]("inclusions")
      val exclusions = unbuilder.readField[Vector[sbt.librarymanagement.InclExclRule]]("exclusions")
      val extraAttributes = unbuilder.readField[Map[String, String]]("extraAttributes")
      val crossVersion = unbuilder.readField[sbt.librarymanagement.CrossVersion]("crossVersion")
      val branchName = unbuilder.readField[Option[String]]("branchName")
      val platformOpt = unbuilder.readField[Option[String]]("platformOpt")
      unbuilder.endObject()
      sbt.librarymanagement.ModuleID(organization, name, revision, configurations, isChanging, isTransitive, isForce, explicitArtifacts, inclusions, exclusions, extraAttributes, crossVersion, branchName, platformOpt)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.ModuleID, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("organization", obj.organization)
    builder.addField("name", obj.name)
    builder.addField("revision", obj.revision)
    builder.addField("configurations", obj.configurations)
    builder.addField("isChanging", obj.isChanging)
    builder.addField("isTransitive", obj.isTransitive)
    builder.addField("isForce", obj.isForce)
    builder.addField("explicitArtifacts", obj.explicitArtifacts)
    builder.addField("inclusions", obj.inclusions)
    builder.addField("exclusions", obj.exclusions)
    builder.addField("extraAttributes", obj.extraAttributes)
    builder.addField("crossVersion", obj.crossVersion)
    builder.addField("branchName", obj.branchName)
    builder.addField("platformOpt", obj.platformOpt)
    builder.endObject()
  }
}
}
