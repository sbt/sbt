/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package server

import java.net.URI
import sbt.internal.bsp._
import sbt.internal.util.complete.DefaultParsers
import sbt.librarymanagement.{ Configuration, Configurations }
import Configurations.{ Compile, Test }
import sbt.SlashSyntax0._
import sbt.BuildSyntax._
import scala.collection.mutable
import sjsonnew.support.scalajson.unsafe.Converter
import Def._
import Keys._
import ScopeFilter.Make._

object BuildServerProtocol {
  private[sbt] val idMap: mutable.Map[BuildTargetIdentifier, (ProjectRef, Configuration)] =
    mutable.Map.empty

  def commands: List[Command] = List()

  lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
    // https://github.com/build-server-protocol/build-server-protocol/blob/master/docs/specification.md#build-target-sources-request
    bspBuildTargetSources := (Def.inputTaskDyn {
      import DefaultParsers._
      val s = state.value
      val args: Seq[String] = spaceDelimited("<arg>").parsed
      val filter = toScopeFilter(args)
      // run bspBuildTargetSourceItem concurrently
      Def.task {
        import sbt.internal.bsp.codec.JsonProtocol._
        val items = bspBuildTargetSourceItem.all(filter).value
        val result = SourcesResult(items.toVector)
        s.respondEvent(result)
      }
    }).evaluated
  )

  // This will be coped to Compile, Test, etc
  lazy val configSettings: Seq[Def.Setting[_]] = Seq(
    buildTargetIdentifier := {
      val ref = thisProjectRef.value
      val c = configuration.value
      toId(ref, c)
    },
    bspBuildTargetSourceItem := {
      val id = buildTargetIdentifier.value
      val dirs = unmanagedSourceDirectories.value
      val managed = managedSources.value
      val items = (dirs.toVector map { dir =>
        SourceItem(dir.toURI, SourceItemKind.Directory, false)
      }) ++
        (managed.toVector map { x =>
          SourceItem(x.toURI, SourceItemKind.File, true)
        })
      SourcesItem(id, items)
    }
  )

  def toScopeFilter(args: Seq[String]): ScopeFilter = {
    val filters = args map { arg =>
      val id = BuildTargetIdentifier(new URI(arg))
      val pair = idMap(id)
      ScopeFilter(inProjects(pair._1), inConfigurations(pair._2))
    }
    filters.tail.foldLeft(filters.head) { _ || _ }
  }

  def toId(ref: ProjectReference, config: Configuration): BuildTargetIdentifier =
    ref match {
      case ProjectRef(build, project) =>
        BuildTargetIdentifier(new URI(s"$build#$project/${config.id}"))
      case _ => sys.error(s"unexpected $ref")
    }
}

// This is mixed into NetworkChannel
trait BuildServerImpl { self: LanguageServerProtocol with CommandChannel =>
  import BuildServerProtocol._

  protected def structure: BuildStructure
  protected def getSetting[A](key: SettingKey[A]): Option[A]
  protected def setting[A](key: SettingKey[A]): A

  protected def onBspInitialize(execId: Option[String], param: InitializeBuildParams): Unit = {
    import sbt.internal.bsp.codec.JsonProtocol._
    val bspVersion = "2.0.0-M5"
    // https://microsoft.github.io/language-server-protocol/specification#textDocumentItem
    val languageIds = Vector("scala")
    val sbtV = setting(Keys.sbtVersion)
    val response =
      InitializeBuildResult("sbt", sbtV, bspVersion, BuildClientCapabilities(languageIds), None)
    jsonRpcRespond(response, execId)
  }

  /**
   * https://github.com/build-server-protocol/build-server-protocol/blob/master/docs/specification.md#workspace-build-targets-request
   */
  protected def onBspBuildTargets(execId: Option[String]): Unit = {
    import sbt.internal.bsp.codec.JsonProtocol._
    val targets = buildTargets
    jsonRpcRespond(targets, execId)
  }

  def buildTargets: WorkspaceBuildTargetsResult = {
    import sbt.internal.bsp.codec.JsonProtocol._
    val allProjectPairs = structure.allProjectPairs
    val ts = allProjectPairs flatMap {
      case (p, ref) =>
        val baseOpt = getSetting(ref / Keys.baseDirectory).map(_.toURI)
        val internalCompileDeps = p.dependencies.flatMap(idForConfig(_, Compile)).toVector
        val compileId = getSetting(ref / Compile / Keys.buildTargetIdentifier).get
        idMap(compileId) = (ref, Compile)
        val compileData = ScalaBuildTarget(
          scalaOrganization = getSetting(ref / Compile / Keys.scalaOrganization).get,
          scalaVersion = getSetting(ref / Compile / Keys.scalaVersion).get,
          scalaBinaryVersion = getSetting(ref / Compile / Keys.scalaBinaryVersion).get,
          platform = ScalaPlatform.JVM,
          jars = Vector("scala-library")
        )
        val t0 = BuildTarget(
          compileId,
          displayName = Some(p.id),
          baseDirectory = baseOpt,
          tags = Vector.empty,
          languageIds = Vector("scala"),
          dependencies = internalCompileDeps,
          dataKind = Some("scala"),
          data = Some(Converter.toJsonUnsafe(compileData)),
        )
        val testId = getSetting(ref / Test / Keys.buildTargetIdentifier).get
        idMap(testId) = (ref, Test)
        // encode Test extending Compile
        val internalTestDeps =
          (p.dependencies ++ Seq(ResolvedClasspathDependency(ref, Some("test->compile"))))
            .flatMap(idForConfig(_, Test))
            .toVector
        val testData = ScalaBuildTarget(
          scalaOrganization = getSetting(ref / Test / Keys.scalaOrganization).get,
          scalaVersion = getSetting(ref / Test / Keys.scalaVersion).get,
          scalaBinaryVersion = getSetting(ref / Test / Keys.scalaBinaryVersion).get,
          platform = ScalaPlatform.JVM,
          jars = Vector("scala-library")
        )
        val t1 = BuildTarget(
          testId,
          displayName = Some(p.id + " test"),
          baseDirectory = baseOpt,
          tags = Vector.empty,
          languageIds = Vector("scala"),
          dependencies = internalTestDeps,
          dataKind = Some("scala"),
          data = Some(Converter.toJsonUnsafe(testData)),
        )
        Seq(t0, t1)
    }
    WorkspaceBuildTargetsResult(ts.toVector)
  }

  def idForConfig(
      ref: ClasspathDep[ProjectRef],
      from: Configuration
  ): Seq[BuildTargetIdentifier] = {
    val configStr = ref.configuration.getOrElse("compile")
    val configExprs0 = configStr.split(",").toList
    val configExprs1 = configExprs0 map { expr =>
      if (expr.contains("->")) {
        val xs = expr.split("->")
        (xs(0), xs(1))
      } else ("compile", expr)
    }
    configExprs1 flatMap {
      case (fr, "compile") if fr == from.name =>
        Some(getSetting(ref.project / Compile / Keys.buildTargetIdentifier).get)
      case (fr, "test") if fr == from.name =>
        Some(getSetting(ref.project / Test / Keys.buildTargetIdentifier).get)
      case _ => None
    }
  }
}
