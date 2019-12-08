/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal.util.codec

import sbt.util.ShowLines
import sbt.internal.util.SuccessEvent

trait SuccessEventShowLines {
  implicit val sbtSuccessEventShowLines: ShowLines[SuccessEvent] =
    ShowLines[SuccessEvent]((e: SuccessEvent) => {
      Vector(e.message)
    })
}

object SuccessEventShowLines extends SuccessEventShowLines
