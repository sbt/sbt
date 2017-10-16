package net.virtualvoid.sbt.graph

import DependencyGraphKeys._

import sbt._
import Keys._

object compat {
  def convertConfig(config: sbt.Configuration): sbt.Configuration = {
    config
  }

  val ingnoreMissingSettings: Seq[Setting[_]] = Seq(
    updateConfiguration in ignoreMissingUpdate := {
      updateConfiguration.value.withMissingOk(true)
    },
    ignoreMissingUpdate := sbt.Compat.updateTask(ignoreMissingUpdate).value
  )
}