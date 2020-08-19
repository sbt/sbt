/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import sbt.librarymanagement.{ Disabled, ModuleID }
import sbt.internal.librarymanagement.mavenint.PomExtraDependencyAttributes

object SbtPluginExtra {
  def apply(m: ModuleID, sbtV: String, scalaV: String): ModuleID =
    m.extra(
        PomExtraDependencyAttributes.SbtVersionKey -> sbtV,
        PomExtraDependencyAttributes.ScalaVersionKey -> scalaV
      )
      .withCrossVersion(Disabled())
}
