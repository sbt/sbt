/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait GetClassifiersConfigurationFormats { self: sbt.librarymanagement.GetClassifiersModuleFormats & sbt.librarymanagement.ModuleIDFormats & sbt.librarymanagement.ArtifactFormats & sbt.librarymanagement.ConfigRefFormats & sbt.librarymanagement.ChecksumFormats & sjsonnew.BasicJsonProtocol & sbt.librarymanagement.InclExclRuleFormats & sbt.librarymanagement.CrossVersionFormats & sbt.librarymanagement.DisabledFormats & sbt.librarymanagement.BinaryFormats & sbt.librarymanagement.ConstantFormats & sbt.librarymanagement.PatchFormats & sbt.librarymanagement.FullFormats & sbt.librarymanagement.For3Use2_13Formats & sbt.librarymanagement.For2_13Use3Formats & sbt.librarymanagement.ScalaModuleInfoFormats & sbt.librarymanagement.ConfigurationFormats & sbt.librarymanagement.UpdateConfigurationFormats & sbt.librarymanagement.RetrieveConfigurationFormats & sbt.librarymanagement.UpdateLoggingFormats & sbt.internal.librarymanagement.formats.LogicalClockFormats & sbt.librarymanagement.ArtifactTypeFilterFormats =>
implicit lazy val GetClassifiersConfigurationFormat: JsonFormat[sbt.librarymanagement.GetClassifiersConfiguration] = new JsonFormat[sbt.librarymanagement.GetClassifiersConfiguration] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.GetClassifiersConfiguration = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val module = unbuilder.readField[sbt.librarymanagement.GetClassifiersModule]("module")
      val excludes = unbuilder.readField[Vector[scala.Tuple2[sbt.librarymanagement.ModuleID, scala.Vector[sbt.librarymanagement.ConfigRef]]]]("excludes")
      val updateConfiguration = unbuilder.readField[sbt.librarymanagement.UpdateConfiguration]("updateConfiguration")
      val sourceArtifactTypes = unbuilder.readField[Vector[String]]("sourceArtifactTypes")
      val docArtifactTypes = unbuilder.readField[Vector[String]]("docArtifactTypes")
      unbuilder.endObject()
      sbt.librarymanagement.GetClassifiersConfiguration(module, excludes, updateConfiguration, sourceArtifactTypes, docArtifactTypes)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.GetClassifiersConfiguration, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("module", obj.module)
    builder.addField("excludes", obj.excludes)
    builder.addField("updateConfiguration", obj.updateConfiguration)
    builder.addField("sourceArtifactTypes", obj.sourceArtifactTypes)
    builder.addField("docArtifactTypes", obj.docArtifactTypes)
    builder.endObject()
  }
}
}
