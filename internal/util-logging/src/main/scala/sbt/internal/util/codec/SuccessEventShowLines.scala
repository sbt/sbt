/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal.util.codec

import sbt.util.ShowLines
import sbt.internal.util.SuccessEvent

trait SuccessEventShowLines {
  given sbtSuccessEventShowLines: ShowLines[SuccessEvent] =
    ShowLines[SuccessEvent]((e: SuccessEvent) => {
      Vector(e.message)
    })
}

object SuccessEventShowLines extends SuccessEventShowLines
