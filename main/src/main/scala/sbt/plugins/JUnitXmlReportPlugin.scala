/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import java.io.File

import Def.{ Setting, settingKey }
import Defaults.*
import Keys.*
import KeyRanks.*
import sbt.ProjectExtra.inConfig
import sbt.internal.*
import sbt.io.syntax.*
import sbt.librarymanagement.Configurations.Test

/**
 * An experimental plugin that adds the ability for junit-xml to be generated.
 *
 *  To disable this plugin, you need to add:
 *  {{{
 *     val myProject = project in file(".") disablePlugins (plugins.JunitXmlReportPlugin)
 *  }}}
 *
 *  Note:  Using AutoPlugins to enable/disable build features is experimental in sbt 0.13.5.
 */
object JUnitXmlReportPlugin extends AutoPlugin {
  // TODO - If testing becomes its own plugin, we only rely on the core settings.
  override def requires = JvmPlugin
  override def trigger = allRequirements

  object autoImport {
    val testReportsDirectory =
      settingKey[File]("Directory for outputting junit test reports.").withRank(AMinusSetting)
    val testReportXmlCaptureStdOut =
      settingKey[Boolean](
        "If true, capture test framework log output into <system-out> in JUnit XML reports."
      ).withRank(BSetting)
    val testReportXmlCaptureStdErr =
      settingKey[Boolean](
        "If true, capture test framework error output into <system-err> in JUnit XML reports."
      ).withRank(BSetting)

    lazy val testReportSettings: Seq[Setting[?]] = Seq(
      testReportsDirectory := target.value / (prefix(configuration.value.name) + "reports"),
      testReportXmlCaptureStdOut := false,
      testReportXmlCaptureStdErr := false,
      testListeners += Def.uncached {
        JUnitXmlTestsListener(
          testReportsDirectory.value,
          SysProp.legacyTestReport,
          streams.value.log,
          testReportXmlCaptureStdOut.value,
          testReportXmlCaptureStdErr.value
        )
      }
    )
  }

  import autoImport.*

  override lazy val projectSettings: Seq[Setting[?]] =
    inConfig(Test)(testReportSettings)
}
