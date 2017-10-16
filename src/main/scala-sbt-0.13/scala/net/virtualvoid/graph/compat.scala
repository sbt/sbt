package net.virtualvoid.sbt.graph

import sbt._
import Keys._

import CrossVersion._
import DependencyGraphKeys._

object compat {
  def convertConfig(config: sbt.Configuration): String = {
    config.toString
  }

/**
   * This is copied directly from sbt/main/Defaults.java and then changed to update the UpdateConfiguration
   * to ignore missing artifacts.
   */
  def ingnoreMissingSettings: Seq[Setting[_]] = Seq(
    updateConfiguration in ignoreMissingUpdate := {
      val config = updateConfiguration.value
      new UpdateConfiguration(config.retrieve, true, config.logging)
    },
    ignoreMissingUpdate := sbt.Compat.updateTask(ignoreMissingUpdate).value
  )
}