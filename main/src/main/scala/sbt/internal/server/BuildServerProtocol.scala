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
      // run the worker task concurrently
      Def.task {
        import sbt.internal.bsp.codec.JsonProtocol._
        val items = bspBuildTargetSourcesItem.all(filter).value
        val result = SourcesResult(items.toVector)
        s.respondEvent(result)
      }
    }).evaluated,
    bspBuildTargetSources / aggregate := false,
    bspBuildTargetCompile := Def.inputTaskDyn {
      val s: State = state.value
      val args: Seq[String] = spaceDelimited().parsed
      val filter = toScopeFilter(args)
      Def.task {
        import sbt.internal.bsp.codec.JsonProtocol._
        val statusCode = Keys.bspBuildTargetCompileItem.all(filter).value.max
        s.respondEvent(BspCompileResult(None, statusCode))
      }
    }.evaluated,
    bspBuildTargetCompile / aggregate := false,
    bspBuildTargetScalacOptions := (Def.inputTaskDyn {
      import DefaultParsers._
      val s = state.value
      val args: Seq[String] = spaceDelimited("<arg>").parsed
      val filter = toScopeFilter(args)
      // run the worker task concurrently
      Def.task {
        import sbt.internal.bsp.codec.JsonProtocol._
        val items = bspBuildTargetScalacOptionsItem.all(filter).value
        val result = ScalacOptionsResult(items.toVector)
        s.respondEvent(result)
      }
    }).evaluated,
    bspBuildTargetScalacOptions / aggregate := false
  )

  // This will be scoped to Compile, Test, etc
  lazy val configSettings: Seq[Def.Setting[_]] = Seq(
    buildTargetIdentifier := {
      val ref = thisProjectRef.value
      val c = configuration.value
      toId(ref, c)
    },
    bspBuildTarget := bspBuildTargetSetting.value,
    bspBuildTargetSourcesItem := {
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
    },
    bspBuildTargetCompileItem := bspCompileTask.value,
    bspBuildTargetScalacOptionsItem :=
      ScalacOptionsItem(
        target = buildTargetIdentifier.value,
        options = scalacOptions.value.toVector,
        classpath = fullClasspath.value.toVector.map(_.data.toURI),
        classDirectory = classDirectory.value.toURI
      )
  )

  private def bspBuildTargetSetting: Def.Initialize[BuildTarget] = Def.settingDyn {
    import sbt.internal.bsp.codec.JsonProtocol._
    val buildTargetIdentifier = Keys.buildTargetIdentifier.value
    val thisProject = Keys.thisProject.value
    val thisProjectRef = Keys.thisProjectRef.value
    val compileData = ScalaBuildTarget(
      scalaOrganization = scalaOrganization.value,
      scalaVersion = scalaVersion.value,
      scalaBinaryVersion = scalaBinaryVersion.value,
      platform = ScalaPlatform.JVM,
      jars = Vector("scala-library")
    )
    val configuration = Keys.configuration.value
    val displayName = configuration.name match {
      case "compile"  => thisProject.id
      case configName => s"${thisProject.id} $configName"
    }
    val baseDirectory = Keys.baseDirectory.value.toURI
    val internalDependencies = configuration.name match {
      case "test" =>
        thisProject.dependencies :+
          ResolvedClasspathDependency(thisProjectRef, Some("test->compile"))
      case _ => thisProject.dependencies
    }
    val dependencies = Initialize.join {
      for {
        dependencyRef <- internalDependencies
        dependencyId <- dependencyTargetKeys(dependencyRef, configuration)
      } yield dependencyId
    }
    Def.setting {
      BuildTarget(
        buildTargetIdentifier,
        Some(displayName),
        Some(baseDirectory),
        tags = Vector.empty,
        languageIds = Vector("scala"),
        dependencies = dependencies.value.toVector,
        dataKind = Some("scala"),
        data = Some(Converter.toJsonUnsafe(compileData)),
      )
    }
  }

  private def bspCompileTask: Def.Initialize[Task[Int]] = Def.task {
    import sbt.Project._
    Keys.compile.result.value match {
      case Value(_) => StatusCode.Success
      case Inc(_)   =>
        // Cancellation is not yet implemented
        StatusCode.Error
    }
  }

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

  private def dependencyTargetKeys(
      ref: ClasspathDep[ProjectRef],
      fromConfig: Configuration
  ): Seq[SettingKey[BuildTargetIdentifier]] = {
    val from = fromConfig.name
    val depConfig = ref.configuration.getOrElse("compile")
    for {
      configExpr <- depConfig.split(",").toSeq
      depId <- configExpr.split("->").toList match {
        case "compile" :: Nil | `from` :: "compile" :: Nil =>
          Some(ref.project / Compile / Keys.buildTargetIdentifier)
        case "test" :: Nil | `from` :: "test" :: Nil =>
          Some(ref.project / Test / Keys.buildTargetIdentifier)
        case _ => None
      }
    } yield depId
  }
}

// This is mixed into NetworkChannel
trait BuildServerImpl { self: LanguageServerProtocol with NetworkChannel =>
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
    respondResult(response, execId)
  }

  /**
   * https://github.com/build-server-protocol/build-server-protocol/blob/master/docs/specification.md#workspace-build-targets-request
   */
  protected def onBspBuildTargets(execId: Option[String]): Unit = {
    import sbt.internal.bsp.codec.JsonProtocol._
    val buildTargets = structure.allProjectRefs.flatMap { ref =>
      val compileTarget = getSetting(ref / Compile / Keys.bspBuildTarget).get
      val testTarget = getSetting(ref / Test / Keys.bspBuildTarget).get
      BuildServerProtocol.idMap ++= Seq(
        compileTarget.id -> ((ref, Compile)),
        testTarget.id -> ((ref, Test))
      )
      Seq(compileTarget, testTarget)
    }
    respondResult(
      WorkspaceBuildTargetsResult(buildTargets.toVector),
      execId
    )
  }
}
