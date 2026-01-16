/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.sona.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait PublisherStatusFormats { self: sbt.internal.sona.codec.DeploymentStateFormats & sjsonnew.BasicJsonProtocol & sbt.internal.util.codec.JValueFormats =>
given PublisherStatusFormat: JsonFormat[sbt.internal.sona.PublisherStatus] = new JsonFormat[sbt.internal.sona.PublisherStatus] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.sona.PublisherStatus = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val deploymentId = unbuilder.readField[String]("deploymentId")
      val deploymentName = unbuilder.readField[String]("deploymentName")
      val deploymentState = unbuilder.readField[sbt.internal.sona.DeploymentState]("deploymentState")
      val purls = unbuilder.readField[Vector[String]]("purls")
      val errors = unbuilder.readField[Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]]("errors")
      unbuilder.endObject()
      sbt.internal.sona.PublisherStatus(deploymentId, deploymentName, deploymentState, purls, errors)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.sona.PublisherStatus, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("deploymentId", obj.deploymentId)
    builder.addField("deploymentName", obj.deploymentName)
    builder.addField("deploymentState", obj.deploymentState)
    builder.addField("purls", obj.purls)
    builder.addField("errors", obj.errors)
    builder.endObject()
  }
}
}
