/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ModuleDescriptorConfigurationFormats { self: sbt.librarymanagement.ScalaModuleInfoFormats with sbt.librarymanagement.ConfigurationFormats with sjsonnew.BasicJsonProtocol with sbt.librarymanagement.ModuleIDFormats with sbt.librarymanagement.ArtifactFormats with sbt.librarymanagement.ConfigRefFormats with sbt.librarymanagement.ChecksumFormats with sbt.librarymanagement.InclExclRuleFormats with sbt.librarymanagement.CrossVersionFormats with sbt.librarymanagement.DisabledFormats with sbt.librarymanagement.BinaryFormats with sbt.librarymanagement.ConstantFormats with sbt.librarymanagement.PatchFormats with sbt.librarymanagement.FullFormats with sbt.librarymanagement.For3Use2_13Formats with sbt.librarymanagement.For2_13Use3Formats with sbt.librarymanagement.ModuleInfoFormats with sbt.librarymanagement.ScmInfoFormats with sbt.librarymanagement.DeveloperFormats with sbt.internal.librarymanagement.formats.NodeSeqFormat with sbt.librarymanagement.ConflictManagerFormats =>
implicit lazy val ModuleDescriptorConfigurationFormat: JsonFormat[sbt.librarymanagement.ModuleDescriptorConfiguration] = new JsonFormat[sbt.librarymanagement.ModuleDescriptorConfiguration] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.ModuleDescriptorConfiguration = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val validate = unbuilder.readField[Boolean]("validate")
      val scalaModuleInfo = unbuilder.readField[Option[sbt.librarymanagement.ScalaModuleInfo]]("scalaModuleInfo")
      val module = unbuilder.readField[sbt.librarymanagement.ModuleID]("module")
      val moduleInfo = unbuilder.readField[sbt.librarymanagement.ModuleInfo]("moduleInfo")
      val dependencies = unbuilder.readField[Vector[sbt.librarymanagement.ModuleID]]("dependencies")
      val overrides = unbuilder.readField[Vector[sbt.librarymanagement.ModuleID]]("overrides")
      val excludes = unbuilder.readField[Vector[sbt.librarymanagement.InclExclRule]]("excludes")
      val ivyXML = unbuilder.readField[scala.xml.NodeSeq]("ivyXML")
      val configurations = unbuilder.readField[Vector[sbt.librarymanagement.Configuration]]("configurations")
      val defaultConfiguration = unbuilder.readField[Option[sbt.librarymanagement.Configuration]]("defaultConfiguration")
      val conflictManager = unbuilder.readField[sbt.librarymanagement.ConflictManager]("conflictManager")
      unbuilder.endObject()
      sbt.librarymanagement.ModuleDescriptorConfiguration(validate, scalaModuleInfo, module, moduleInfo, dependencies, overrides, excludes, ivyXML, configurations, defaultConfiguration, conflictManager)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.ModuleDescriptorConfiguration, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("validate", obj.validate)
    builder.addField("scalaModuleInfo", obj.scalaModuleInfo)
    builder.addField("module", obj.module)
    builder.addField("moduleInfo", obj.moduleInfo)
    builder.addField("dependencies", obj.dependencies)
    builder.addField("overrides", obj.overrides)
    builder.addField("excludes", obj.excludes)
    builder.addField("ivyXML", obj.ivyXML)
    builder.addField("configurations", obj.configurations)
    builder.addField("defaultConfiguration", obj.defaultConfiguration)
    builder.addField("conflictManager", obj.conflictManager)
    builder.endObject()
  }
}
}
