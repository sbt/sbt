/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import Keys._
import Def.Setting
import sbt.SlashSyntax0._
import sbt.librarymanagement.Configurations.Compile
import sbt.librarymanagement.{ SemanticSelector, VersionNumber }

object SbtPlugin extends AutoPlugin {
  override def requires = ScriptedPlugin

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    sbtPlugin := true,
    Compile / scalacOptions ++= {
      // silence unused @nowarns in 2.12 because of https://github.com/sbt/sbt/issues/6398
      // the option is only available since 2.12.13
      if (VersionNumber(scalaVersion.value).matchesSemVer(SemanticSelector("=2.12 >=2.12.13")))
        Some("-Wconf:cat=unused-nowarn:s")
      else
        None
    }
  )
}
