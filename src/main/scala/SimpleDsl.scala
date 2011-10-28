package sbt.extra.dsl

import sbt._
import Scoped._
import Project.{richInitializeTask,richInitialize}

object SimpleTasks {
  final def task(name: String) = new TaskId(name)
  final def setting(name: String) = new SettingId(name)
}
