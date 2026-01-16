/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util.codec

import _root_.sjsonnew.{ deserializationError, Builder, JsonFormat, Unbuilder }
import xsbti.Severity;

trait SeverityFormats { self: sjsonnew.BasicJsonProtocol =>
  given SeverityFormat: JsonFormat[Severity] = new JsonFormat[Severity] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Severity = {
      jsOpt match {
        case Some(js) =>
          unbuilder.readString(js) match {
            case "Info"  => Severity.Info
            case "Warn"  => Severity.Warn
            case "Error" => Severity.Error
          }
        case None =>
          deserializationError("Expected JsString but found None")
      }
    }
    override def write[J](obj: Severity, builder: Builder[J]): Unit = {
      val str = obj match {
        case Severity.Info  => "Info"
        case Severity.Warn  => "Warn"
        case Severity.Error => "Error"
      }
      builder.writeString(str)
    }
  }
}
