/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.sona.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait DeploymentStateFormats { self: sjsonnew.BasicJsonProtocol =>
given DeploymentStateFormat: JsonFormat[sbt.internal.sona.DeploymentState] = new JsonFormat[sbt.internal.sona.DeploymentState] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.sona.DeploymentState = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.readString(__js) match {
        case "PENDING" => sbt.internal.sona.DeploymentState.PENDING
        case "VALIDATING" => sbt.internal.sona.DeploymentState.VALIDATING
        case "VALIDATED" => sbt.internal.sona.DeploymentState.VALIDATED
        case "PUBLISHING" => sbt.internal.sona.DeploymentState.PUBLISHING
        case "PUBLISHED" => sbt.internal.sona.DeploymentState.PUBLISHED
        case "FAILED" => sbt.internal.sona.DeploymentState.FAILED
      }
      case None =>
      deserializationError("Expected JsString but found None")
    }
  }
  override def write[J](obj: sbt.internal.sona.DeploymentState, builder: Builder[J]): Unit = {
    val str = obj match {
      case sbt.internal.sona.DeploymentState.PENDING => "PENDING"
      case sbt.internal.sona.DeploymentState.VALIDATING => "VALIDATING"
      case sbt.internal.sona.DeploymentState.VALIDATED => "VALIDATED"
      case sbt.internal.sona.DeploymentState.PUBLISHING => "PUBLISHING"
      case sbt.internal.sona.DeploymentState.PUBLISHED => "PUBLISHED"
      case sbt.internal.sona.DeploymentState.FAILED => "FAILED"
    }
    builder.writeString(str)
  }
}
}
