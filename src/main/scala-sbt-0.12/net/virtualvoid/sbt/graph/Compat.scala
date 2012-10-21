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
    ignoreMissingUpdate <<= (ivyModule, thisProjectRef, updateConfiguration, cacheDirectory, scalaInstance, transitiveUpdate, executionRoots, resolvedScoped, skip in update, streams) map {
			(module, ref, config, cacheDirectory, si, reports, roots, resolved, skip, s) =>
				val depsUpdated = reports.exists(!_.stats.cached)
				val isRoot = roots contains resolved
        val missingOkConfig = new UpdateConfiguration(config.retrieve, true, config.logging)

				Classpaths.cachedUpdate(cacheDirectory / "update", Project.display(ref), module, missingOkConfig, Some(si), skip = skip, force = isRoot, depsUpdated = depsUpdated, log = s.log)
		} tag(Tags.Update, Tags.Network)
}