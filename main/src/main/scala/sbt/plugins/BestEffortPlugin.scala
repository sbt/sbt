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

import Keys.*
import sbt.internal.SysProp
import sbt.librarymanagement.syntax.*
import sbt.librarymanagement.Configuration
import ProjectExtra.inConfig
import sbt.internal.inc.ScalaInstance
import sbt.ScopeFilter.Make.*
import sbt.util.CacheImplicits.given

/**
 * An AutoPlugin that wires Scala 3's Best Effort compilation into sbt.
 *
 * When `bestEffortEnabled` is true and the project uses Scala 3.5+,
 * the plugin appends `-Ybest-effort` and `-Ybest-effort-dir` to scalacOptions
 * so that the compiler produces .betasty files even when compilation fails.
 * These files are consumed by IDEs such as Metals for improved code intelligence.
 */
object BestEffortPlugin extends AutoPlugin:
  override def requires = JvmPlugin
  override def trigger = allRequirements

  override lazy val globalSettings: Seq[Def.Setting[?]] = Seq(
    bestEffortEnabled := SysProp.bestEffort,
    bestEffortOptions := List(),
  )

  override lazy val projectSettings: Seq[Def.Setting[?]] = Seq(
    bestEffortOptions ++= {
      val enabled = bestEffortEnabled.value
      val sv = scalaVersion.value
      if enabled && isScala35Plus(sv) then Seq("-Ybest-effort")
      else Nil
    },
  ) ++
    inConfig(Compile)(configurationSettings) ++
      inConfig(Test)(configurationSettings)

  lazy val configurationSettings: Seq[Def.Setting[?]] = List(
    compileIncremental := Def.taskIf {
      if !bestEffortEnabled.value then compileIncremental.value
      else compileIncAndCacheBestEffortTargetRootTask.value
    }.value,
    bestEffortOptions --= Def.settingDyn {
      val scalaV = scalaVersion.value
      val config = configuration.value
      Def.setting {
        bestEffortTargetRoot.?.all(ancestorConfigs(config)).value.flatten
          .flatMap(targetRootOptions(scalaV, _))
      }
    }.value,
    bestEffortOptions ++=
      targetRootOptions(scalaVersion.value, bestEffortTargetRoot.value),
    scalacOptions := (Def.taskDyn {
      val orig = scalacOptions.value
      val config = configuration.value
      if bestEffortEnabled.value then
        Def.task {
          (orig diff bestEffortOptions.?.all(ancestorConfigs(config)).value.flatten.flatten) ++
            bestEffortOptions.value
        }
      else
        Def.task {
          orig
        }
    }).value,
  )

  private[sbt] def isScala35Plus(scalaVersion: String): Boolean =
    ScalaInstance.isDotty(scalaVersion) && {
      val versionPart = scalaVersion.stripPrefix("3.")
      val minor = versionPart.takeWhile(_.isDigit)
      minor.nonEmpty && minor.toInt >= 5
    }

  def targetRootOptions(scalaVersion: String, targetRoot: File): Seq[String] =
    if isScala35Plus(scalaVersion) then
      Seq("-Ybest-effort-dir", targetRoot.toString)
    else Nil

  private val compileIncAndCacheBestEffortTargetRootTask = Def.cachedTask {
    val prev = compileIncremental.value
    val converter = fileConverter.value
    val targetRoot = bestEffortTargetRoot.value

    val vfTargetRoot = converter.toVirtualFile(targetRoot.toPath)
    Def.declareOutputDirectory(vfTargetRoot)
    prev
  }

  private def ancestorConfigs(config: Configuration) =
    def ancestors(configs: Vector[Configuration]): Vector[Configuration] =
      configs ++ configs.flatMap(conf => ancestors(conf.extendsConfigs))
    ScopeFilter(configurations = inConfigurations(ancestors(config.extendsConfigs)*))
end BestEffortPlugin
