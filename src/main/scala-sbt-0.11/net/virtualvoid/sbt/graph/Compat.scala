package net.virtualvoid.sbt.graph

import sbt._
import Keys._

import Plugin.ignoreMissingUpdate

object Compat {
  /**
   * This is copied directly from sbt/main/Defaults.java and then changed to update the UpdateConfiguration
   * to ignore missing artifacts.
   */
  def ignoreMissingUpdateT =
    ignoreMissingUpdate <<= (ivyModule, thisProjectRef, updateConfiguration, cacheDirectory, scalaInstance, transitiveUpdate, streams) map { (module, ref, config, cacheDirectory, si, reports, s) =>
			val depsUpdated = reports.exists(!_.stats.cached)
      val missingOkConfig = new UpdateConfiguration(config.retrieve, true, config.logging)

			Classpaths.cachedUpdate(cacheDirectory / "update", Project.display(ref), module, missingOkConfig, Some(si), depsUpdated, s.log)
		}

  import complete.DefaultParsers._
  lazy val StringBasic = NotSpaceClass.*.string
}