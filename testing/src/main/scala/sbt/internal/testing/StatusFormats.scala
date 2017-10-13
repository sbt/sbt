/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.testing

import sbt.testing.Status

import _root_.sjsonnew.{ deserializationError, Builder, JsonFormat, Unbuilder }

trait StatusFormats { self: sjsonnew.BasicJsonProtocol =>
  implicit lazy val StatusFormat: JsonFormat[Status] = new JsonFormat[Status] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Status = {
      jsOpt match {
        case Some(js) =>
          unbuilder.readString(js) match {
            case "Success"  => Status.Success
            case "Error"    => Status.Error
            case "Failure"  => Status.Failure
            case "Skipped"  => Status.Skipped
            case "Ignored"  => Status.Ignored
            case "Canceled" => Status.Canceled
            case "Pending"  => Status.Pending
          }
        case None =>
          deserializationError("Expected JsString but found None")
      }
    }
    override def write[J](obj: Status, builder: Builder[J]): Unit = {
      val str = obj match {
        case Status.Success  => "Success"
        case Status.Error    => "Error"
        case Status.Failure  => "Failure"
        case Status.Skipped  => "Skipped"
        case Status.Ignored  => "Ignored"
        case Status.Canceled => "Canceled"
        case Status.Pending  => "Pending"
      }
      builder.writeString(str)
    }
  }
}
