package sbt
package internal.util.codec

import sbt.util.ShowLines
import sbt.internal.util.SuccessEvent

trait SuccessEventShowLines {
  implicit val sbtSuccessEventShowLines: ShowLines[SuccessEvent] =
    ShowLines[SuccessEvent]( (e: SuccessEvent) => {
      Vector(e.message)
    })
}

object SuccessEventShowLines extends SuccessEventShowLines
