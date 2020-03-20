/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import Keys._
import sbt.librarymanagement.syntax._
import sbt.librarymanagement.CrossVersion
import Project.inConfig

object SemanticdbPlugin extends AutoPlugin {
  override def requires = JvmPlugin
  override def trigger = allRequirements

  override lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
    semanticdbEnabled := false,
    semanticdbIncludeInJar := false,
    semanticdbOptions := List("-Yrangepos"),
    semanticdbVersion := "4.3.7"
  )

  override lazy val projectSettings: Seq[Def.Setting[_]] = Seq(
    semanticdbCompilerPlugin := {
      val v = semanticdbVersion.value
      ("org.scalameta" % "semanticdb-scalac" % v).cross(CrossVersion.full)
    },
    allDependencies ++= {
      val sdb = semanticdbEnabled.value
      val m = semanticdbCompilerPlugin.value
      if (sdb) List(Build0.compilerPlugin(m))
      else Nil
    }
  ) ++ inConfig(Compile)(configurationSettings) ++ inConfig(Test)(configurationSettings)

  lazy val configurationSettings: Seq[Def.Setting[_]] = List(
    scalacOptions := {
      val old = scalacOptions.value
      val sdb = semanticdbEnabled.value
      val sdbOptions = semanticdbOptions.value
      if (sdb) (old.toVector ++ sdbOptions.toVector).distinct
      else old
    },
    semanticdbTargetRoot := {
      val in = semanticdbIncludeInJar.value
      if (in) classDirectory.value
      else semanticdbTargetRoot.value
    },
    semanticdbOptions ++= {
      val tr = semanticdbTargetRoot.value
      List(s"-P:semanticdb:targetroot:$tr")
    }
  )
}
