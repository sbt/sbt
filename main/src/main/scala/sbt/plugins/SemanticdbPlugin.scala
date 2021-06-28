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
import sbt.librarymanagement.{ Configuration, CrossVersion }
import Project.inConfig
import sbt.internal.inc.ScalaInstance
import sbt.ScopeFilter.Make._

object SemanticdbPlugin extends AutoPlugin {
  override def requires = JvmPlugin
  override def trigger = allRequirements

  override lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
    semanticdbEnabled := SysProp.semanticdb,
    semanticdbIncludeInJar := false,
    semanticdbOptions := List(),
    semanticdbVersion := "4.4.20"
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
      if (sv.startsWith("0.") || sv.startsWith("3.0.0-M1") || sv.startsWith("3.0.0-M2"))
        "-Ysemanticdb"
      else if (sv.startsWith("3.")) "-Xsemanticdb"
      else "-Yrangepos"
    }
  ) ++
    inConfig(Compile)(configurationSettings) ++
    inConfig(Test)(configurationSettings)

  lazy val configurationSettings: Seq[Def.Setting[_]] = List(
    semanticdbTargetRoot := {
      val in = semanticdbIncludeInJar.value
      if (in) classDirectory.value
      else semanticdbTargetRoot.value
    },
    semanticdbOptions --= Def.settingDyn {
      val scalaV = scalaVersion.value
      val config = configuration.value
      Def.setting {
        semanticdbTargetRoot.?.all(ancestorConfigs(config)).value.flatten
          .flatMap(targetRootOptions(scalaV, _))
      }
    }.value,
    semanticdbOptions ++=
      targetRootOptions(scalaVersion.value, semanticdbTargetRoot.value),
    scalacOptions --= Def.settingDyn {
      val config = configuration.value
      val enabled = semanticdbEnabled.value
      if (enabled)
        Def.setting {
          semanticdbOptions.?.all(ancestorConfigs(config)).value.flatten.flatten
        } else Def.setting { Nil }
    }.value,
    scalacOptions ++= {
      if (semanticdbEnabled.value)
        semanticdbOptions.value
      else Seq.empty
    }
  )

  @deprecated("use configurationSettings only", "1.5.0")
  lazy val testSettings: Seq[Def.Setting[_]] = List()

  def targetRootOptions(scalaVersion: String, targetRoot: File): Seq[String] = {
    if (ScalaInstance.isDotty(scalaVersion)) {
      Seq("-semanticdb-target", targetRoot.toString)
    } else {
      Seq(s"-P:semanticdb:targetroot:$targetRoot")
    }
  }

  private def ancestorConfigs(config: Configuration) = {
    def ancestors(configs: Vector[Configuration]): Vector[Configuration] =
      configs ++ configs.flatMap(conf => ancestors(conf.extendsConfigs))

    ScopeFilter(configurations = inConfigurations(ancestors(config.extendsConfigs): _*))
  }
}
