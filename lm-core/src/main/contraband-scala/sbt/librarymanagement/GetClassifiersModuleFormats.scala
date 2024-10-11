/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait GetClassifiersModuleFormats { self: sbt.librarymanagement.ModuleIDFormats with sbt.librarymanagement.ArtifactFormats with sbt.librarymanagement.ConfigRefFormats with sbt.librarymanagement.ChecksumFormats with sjsonnew.BasicJsonProtocol with sbt.librarymanagement.InclExclRuleFormats with sbt.librarymanagement.CrossVersionFormats with sbt.librarymanagement.DisabledFormats with sbt.librarymanagement.BinaryFormats with sbt.librarymanagement.ConstantFormats with sbt.librarymanagement.PatchFormats with sbt.librarymanagement.FullFormats with sbt.librarymanagement.For3Use2_13Formats with sbt.librarymanagement.For2_13Use3Formats with sbt.librarymanagement.ScalaModuleInfoFormats with sbt.librarymanagement.ConfigurationFormats =>
implicit lazy val GetClassifiersModuleFormat: JsonFormat[sbt.librarymanagement.GetClassifiersModule] = new JsonFormat[sbt.librarymanagement.GetClassifiersModule] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.GetClassifiersModule = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val id = unbuilder.readField[sbt.librarymanagement.ModuleID]("id")
      val scalaModuleInfo = unbuilder.readField[Option[sbt.librarymanagement.ScalaModuleInfo]]("scalaModuleInfo")
      val dependencies = unbuilder.readField[Vector[sbt.librarymanagement.ModuleID]]("dependencies")
      val configurations = unbuilder.readField[Vector[sbt.librarymanagement.Configuration]]("configurations")
      val classifiers = unbuilder.readField[Vector[String]]("classifiers")
      unbuilder.endObject()
      sbt.librarymanagement.GetClassifiersModule(id, scalaModuleInfo, dependencies, configurations, classifiers)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.GetClassifiersModule, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("id", obj.id)
    builder.addField("scalaModuleInfo", obj.scalaModuleInfo)
    builder.addField("dependencies", obj.dependencies)
    builder.addField("configurations", obj.configurations)
    builder.addField("classifiers", obj.classifiers)
    builder.endObject()
  }
}
}
