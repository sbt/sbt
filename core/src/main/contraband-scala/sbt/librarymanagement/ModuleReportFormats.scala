/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ModuleReportFormats { self: sbt.librarymanagement.ModuleIDFormats with sbt.librarymanagement.ArtifactFormats with sbt.librarymanagement.ConfigRefFormats with sbt.librarymanagement.ChecksumFormats with sjsonnew.BasicJsonProtocol with sbt.librarymanagement.InclExclRuleFormats with sbt.librarymanagement.CrossVersionFormats with sbt.librarymanagement.DisabledFormats with sbt.librarymanagement.BinaryFormats with sbt.librarymanagement.ConstantFormats with sbt.librarymanagement.PatchFormats with sbt.librarymanagement.FullFormats with sbt.librarymanagement.For3Use2_13Formats with sbt.librarymanagement.For2_13Use3Formats with sbt.librarymanagement.CallerFormats =>
implicit lazy val ModuleReportFormat: JsonFormat[sbt.librarymanagement.ModuleReport] = new JsonFormat[sbt.librarymanagement.ModuleReport] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.ModuleReport = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val module = unbuilder.readField[sbt.librarymanagement.ModuleID]("module")
      val artifacts = unbuilder.readField[Vector[scala.Tuple2[sbt.librarymanagement.Artifact, java.io.File]]]("artifacts")
      val missingArtifacts = unbuilder.readField[Vector[sbt.librarymanagement.Artifact]]("missingArtifacts")
      val status = unbuilder.readField[Option[String]]("status")
      val publicationDate = unbuilder.readField[Option[java.util.Calendar]]("publicationDate")
      val resolver = unbuilder.readField[Option[String]]("resolver")
      val artifactResolver = unbuilder.readField[Option[String]]("artifactResolver")
      val evicted = unbuilder.readField[Boolean]("evicted")
      val evictedData = unbuilder.readField[Option[String]]("evictedData")
      val evictedReason = unbuilder.readField[Option[String]]("evictedReason")
      val problem = unbuilder.readField[Option[String]]("problem")
      val homepage = unbuilder.readField[Option[String]]("homepage")
      val extraAttributes = unbuilder.readField[Map[String, String]]("extraAttributes")
      val isDefault = unbuilder.readField[Option[Boolean]]("isDefault")
      val branch = unbuilder.readField[Option[String]]("branch")
      val configurations = unbuilder.readField[Vector[sbt.librarymanagement.ConfigRef]]("configurations")
      val licenses = unbuilder.readField[Vector[scala.Tuple2[String, Option[String]]]]("licenses")
      val callers = unbuilder.readField[Vector[sbt.librarymanagement.Caller]]("callers")
      unbuilder.endObject()
      sbt.librarymanagement.ModuleReport(module, artifacts, missingArtifacts, status, publicationDate, resolver, artifactResolver, evicted, evictedData, evictedReason, problem, homepage, extraAttributes, isDefault, branch, configurations, licenses, callers)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.ModuleReport, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("module", obj.module)
    builder.addField("artifacts", obj.artifacts)
    builder.addField("missingArtifacts", obj.missingArtifacts)
    builder.addField("status", obj.status)
    builder.addField("publicationDate", obj.publicationDate)
    builder.addField("resolver", obj.resolver)
    builder.addField("artifactResolver", obj.artifactResolver)
    builder.addField("evicted", obj.evicted)
    builder.addField("evictedData", obj.evictedData)
    builder.addField("evictedReason", obj.evictedReason)
    builder.addField("problem", obj.problem)
    builder.addField("homepage", obj.homepage)
    builder.addField("extraAttributes", obj.extraAttributes)
    builder.addField("isDefault", obj.isDefault)
    builder.addField("branch", obj.branch)
    builder.addField("configurations", obj.configurations)
    builder.addField("licenses", obj.licenses)
    builder.addField("callers", obj.callers)
    builder.endObject()
  }
}
}
