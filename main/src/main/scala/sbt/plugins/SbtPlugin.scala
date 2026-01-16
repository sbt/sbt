/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import sbt.Def.Setting
import sbt.Keys.*

object SbtPlugin extends AutoPlugin:
  override def requires = ScriptedPlugin

  override lazy val projectSettings: Seq[Setting[?]] = Seq(
    sbtPlugin := true,
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match
        case "3"    => sbtVersion.value
        case "2.12" => "1.5.8"
        case "2.10" => "0.13.18"
    },
  )
end SbtPlugin
