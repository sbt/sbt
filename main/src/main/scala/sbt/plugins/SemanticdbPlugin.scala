/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import java.io.File

import Keys._
import sbt.internal.SysProp
import sbt.librarymanagement.syntax._
import sbt.librarymanagement.CrossVersion
import Project.inConfig
import sbt.internal.inc.ScalaInstance
import sbt.SlashSyntax0._

object SemanticdbPlugin extends AutoPlugin {
  override def requires = JvmPlugin
  override def trigger = allRequirements

  override lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
    semanticdbEnabled := SysProp.semanticdb,
    semanticdbIncludeInJar := false,
    semanticdbOptions := List(),
    semanticdbVersion := "4.4.0"
  )

  override lazy val projectSettings: Seq[Def.Setting[_]] = Seq(
    semanticdbCompilerPlugin := {
      val v = semanticdbVersion.value
      ("org.scalameta" % "semanticdb-scalac" % v).cross(CrossVersion.full)
    },
    allDependencies ++= {
      val sdb = semanticdbEnabled.value
      val m = semanticdbCompilerPlugin.value
      val sv = scalaVersion.value
      if (sdb && !ScalaInstance.isDotty(sv)) List(Build0.compilerPlugin(m))
      else Nil
    },
    semanticdbOptions += {
      val sv = scalaVersion.value
      if (ScalaInstance.isDotty(sv)) "-Ysemanticdb"
      else "-Yrangepos"
    }
  ) ++
    inConfig(Compile)(configurationSettings) ++
    inConfig(Test)(configurationSettings ++ testSettings)

  lazy val configurationSettings: Seq[Def.Setting[_]] = List(
    semanticdbTargetRoot := {
      val in = semanticdbIncludeInJar.value
      if (in) classDirectory.value
      else semanticdbTargetRoot.value
    },
    semanticdbOptions ++=
      targetRootOptions(scalaVersion.value, semanticdbTargetRoot.value),
    scalacOptions ++= {
      if (semanticdbEnabled.value)
        semanticdbOptions.value
      else Seq.empty
    }
  )

  lazy val testSettings: Seq[Def.Setting[_]] = List(
    // remove Compile targetRoot from Test config
    semanticdbOptions --= targetRootOptions(
      (Compile / scalaVersion).value,
      (Compile / semanticdbTargetRoot).value
    ),
    // remove duplicated semanticdbOptions
    scalacOptions --= (Compile / semanticdbOptions).value
  )

  def targetRootOptions(scalaVersion: String, targetRoot: File): Seq[String] = {
    if (ScalaInstance.isDotty(scalaVersion)) {
      Seq("-semanticdb-target", targetRoot.toString)
    } else {
      Seq(s"-P:semanticdb:targetroot:$targetRoot")
    }
  }
}
