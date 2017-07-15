/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait GetClassifiersModuleFormats { self: sbt.librarymanagement.ModuleIDFormats with sbt.librarymanagement.ScalaModuleInfoFormats with sbt.librarymanagement.ConfigurationFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val GetClassifiersModuleFormat: JsonFormat[sbt.librarymanagement.GetClassifiersModule] = new JsonFormat[sbt.librarymanagement.GetClassifiersModule] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.GetClassifiersModule = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
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
