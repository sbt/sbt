/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CallerFormats { self: sbt.librarymanagement.ModuleIDFormats with sbt.librarymanagement.ArtifactFormats with sbt.librarymanagement.ConfigRefFormats with sbt.librarymanagement.ChecksumFormats with sjsonnew.BasicJsonProtocol with sbt.librarymanagement.InclExclRuleFormats with sbt.librarymanagement.CrossVersionFormats with sbt.librarymanagement.DisabledFormats with sbt.librarymanagement.BinaryFormats with sbt.librarymanagement.ConstantFormats with sbt.librarymanagement.PatchFormats with sbt.librarymanagement.FullFormats with sbt.librarymanagement.For3Use2_13Formats with sbt.librarymanagement.For2_13Use3Formats =>
implicit lazy val CallerFormat: JsonFormat[sbt.librarymanagement.Caller] = new JsonFormat[sbt.librarymanagement.Caller] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.Caller = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val caller = unbuilder.readField[sbt.librarymanagement.ModuleID]("caller")
      val callerConfigurations = unbuilder.readField[Vector[sbt.librarymanagement.ConfigRef]]("callerConfigurations")
      val callerExtraAttributes = unbuilder.readField[Map[String, String]]("callerExtraAttributes")
      val isForceDependency = unbuilder.readField[Boolean]("isForceDependency")
      val isChangingDependency = unbuilder.readField[Boolean]("isChangingDependency")
      val isTransitiveDependency = unbuilder.readField[Boolean]("isTransitiveDependency")
      val isDirectlyForceDependency = unbuilder.readField[Boolean]("isDirectlyForceDependency")
      unbuilder.endObject()
      sbt.librarymanagement.Caller(caller, callerConfigurations, callerExtraAttributes, isForceDependency, isChangingDependency, isTransitiveDependency, isDirectlyForceDependency)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.Caller, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("caller", obj.caller)
    builder.addField("callerConfigurations", obj.callerConfigurations)
    builder.addField("callerExtraAttributes", obj.callerExtraAttributes)
    builder.addField("isForceDependency", obj.isForceDependency)
    builder.addField("isChangingDependency", obj.isChangingDependency)
    builder.addField("isTransitiveDependency", obj.isTransitiveDependency)
    builder.addField("isDirectlyForceDependency", obj.isDirectlyForceDependency)
    builder.endObject()
  }
}
}
