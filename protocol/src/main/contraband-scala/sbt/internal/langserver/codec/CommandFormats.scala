/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ deserializationError, serializationError, Builder, JsonFormat, Unbuilder }
trait CommandFormats {
  implicit lazy val CommandFormat: JsonFormat[sbt.internal.langserver.Command] = new JsonFormat[sbt.internal.langserver.Command] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.Command = {
      deserializationError("No known implementation of Command.")
    }
    override def write[J](obj: sbt.internal.langserver.Command, builder: Builder[J]): Unit = {
      serializationError("No known implementation of Command.")
    }
  }
}
