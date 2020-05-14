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

import sbt.BuildSyntax._
import sbt.Def._
import sbt.Keys._
import sbt.ScopeFilter.Make._
import sbt.SlashSyntax0._
import sbt.internal.bsp._
import sbt.internal.langserver.ErrorCodes
import sbt.internal.protocol.JsonRpcRequestMessage
import sbt.librarymanagement.Configuration
import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import sjsonnew.support.scalajson.unsafe.Converter

object BuildServerProtocol {
  import sbt.internal.bsp.codec.JsonProtocol._
  private val bspVersion = "2.0.0-M5"
  private val languageIds = Vector("scala")
  private val bspTargetConfigs = Set("compile", "test")
  private val capabilities = BuildServerCapabilities(
    CompileProvider(languageIds),
    dependencySourcesProvider = true
  )

  lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
    bspWorkspace := Def.settingDyn {
      val loadedBuild = Keys.loadedBuild.value
      val scopes: Seq[Scope] = loadedBuild.allProjectRefs.flatMap {
        case (ref, _) =>
          bspTargetConfigs.toSeq.map(name => Scope.Global.in(ref, ConfigKey(name)))
      }
      Def.setting {
        val targetIds = scopes.map(_ / Keys.bspTargetIdentifier).join.value
        targetIds.zip(scopes).toMap
      }
    }.value,
    bspWorkspaceBuildTargets := Def.taskDyn {
      val workspace = Keys.bspWorkspace.value
      val state = Keys.state.value
      val allTargets = ScopeFilter.in(workspace.values.toSeq)
      Def.task {
        val buildTargets = Keys.bspBuildTarget.all(allTargets).value.toVector
        state.respondEvent(WorkspaceBuildTargetsResult(buildTargets))
        buildTargets
      }
    }.value,
    // https://github.com/build-server-protocol/build-server-protocol/blob/master/docs/specification.md#build-target-sources-request
    bspBuildTargetSources := Def.inputTaskDyn {
      val s = state.value
      val workspace = bspWorkspace.value
      val targets = spaceDelimited().parsed.map(uri => BuildTargetIdentifier(URI.create(uri)))
      val filter = ScopeFilter.in(targets.map(workspace))
      // run the worker task concurrently
      Def.task {
        import sbt.internal.bsp.codec.JsonProtocol._
        val items = bspBuildTargetSourcesItem.all(filter).value
        val result = SourcesResult(items.toVector)
        s.respondEvent(result)
      }
    }.evaluated,
    bspBuildTargetSources / aggregate := false,
    bspBuildTargetDependencySources := Def.inputTaskDyn {
      val s = state.value
      val workspace = bspWorkspace.value
      val targets = spaceDelimited().parsed.map(uri => BuildTargetIdentifier(URI.create(uri)))
      val filter = ScopeFilter.in(targets.map(workspace))
      // run the worker task concurrently
      Def.task {
        import sbt.internal.bsp.codec.JsonProtocol._
        val items = bspBuildTargetDependencySourcesItem.all(filter).value
        val result = DependencySourcesResult(items.toVector)
        s.respondEvent(result)
      }
    }.evaluated,
    bspBuildTargetDependencySources / aggregate := false,
    bspBuildTargetCompile := Def.inputTaskDyn {
      val s: State = state.value
      val workspace = bspWorkspace.value
      val targets = spaceDelimited().parsed.map(uri => BuildTargetIdentifier(URI.create(uri)))
      val filter = ScopeFilter.in(targets.map(workspace))
      Def.task {
        import sbt.internal.bsp.codec.JsonProtocol._
        val statusCode = Keys.bspBuildTargetCompileItem.all(filter).value.max
        s.respondEvent(BspCompileResult(None, statusCode))
      }
    }.evaluated,
    bspBuildTargetCompile / aggregate := false,
    bspBuildTargetScalacOptions := Def.inputTaskDyn {
      val s = state.value
      val workspace = bspWorkspace.value
      val targets = spaceDelimited().parsed.map(uri => BuildTargetIdentifier(URI.create(uri)))
      val filter = ScopeFilter.in(targets.map(workspace))
      Def.task {
        val items = bspBuildTargetScalacOptionsItem.all(filter).value
        val result = ScalacOptionsResult(items.toVector)
        s.respondEvent(result)
      }
    }.evaluated,
    bspBuildTargetScalacOptions / aggregate := false
  )

  // This will be scoped to Compile, Test, etc
  lazy val configSettings: Seq[Def.Setting[_]] = Seq(
    bspTargetIdentifier := {
      val ref = thisProjectRef.value
      val c = configuration.value
      toId(ref, c)
    },
    bspBuildTarget := buildTargetTask.value,
    bspBuildTargetSourcesItem := {
      val id = bspTargetIdentifier.value
      val dirs = unmanagedSourceDirectories.value
      val managed = managedSources.value
      val items = (dirs.toVector map { dir =>
        SourceItem(dir.toURI, SourceItemKind.Directory, generated = false)
      }) ++
        (managed.toVector map { x =>
          SourceItem(x.toURI, SourceItemKind.File, generated = true)
        })
      SourcesItem(id, items)
    },
    bspBuildTargetDependencySourcesItem := dependencySourcesItemTask.value,
    bspBuildTargetCompileItem := bspCompileTask.value,
    bspBuildTargetScalacOptionsItem := scalacOptionsTask.value,
    bspInternalDependencyConfigurations := internalDependencyConfigurationsSetting.value
  )

  def handler(sbtVersion: String): ServerHandler = ServerHandler { callback =>
    ServerIntent(
      {
        case r: JsonRpcRequestMessage if r.method == "build/initialize" =>
          val _ = Converter.fromJson[InitializeBuildParams](json(r)).get
          val response = InitializeBuildResult("sbt", sbtVersion, bspVersion, capabilities, None)
          callback.jsonRpcRespond(response, Some(r.id)); ()

        case r: JsonRpcRequestMessage if r.method == "workspace/buildTargets" =>
          val _ = callback.appendExec(Keys.bspWorkspaceBuildTargets.key.toString, Some(r.id))

        case r: JsonRpcRequestMessage if r.method == "build/shutdown" =>
          ()

        case r: JsonRpcRequestMessage if r.method == "build/exit" =>
          val _ = callback.appendExec("shutdown", Some(r.id))

        case r: JsonRpcRequestMessage if r.method == "buildTarget/sources" =>
          val param = Converter.fromJson[SourcesParams](json(r)).get
          val targets = param.targets.map(_.uri).mkString(" ")
          val command = Keys.bspBuildTargetSources.key
          val _ = callback.appendExec(s"$command $targets", Some(r.id))

        case r if r.method == "buildTarget/dependencySources" =>
          val param = Converter.fromJson[DependencySourcesParams](json(r)).get
          val targets = param.targets.map(_.uri).mkString(" ")
          val command = Keys.bspBuildTargetDependencySources.key
          val _ = callback.appendExec(s"$command $targets", Some(r.id))

        case r if r.method == "buildTarget/compile" =>
          val param = Converter.fromJson[CompileParams](json(r)).get
          callback.log.info(param.toString)
          val targets = param.targets.map(_.uri).mkString(" ")
          val command = Keys.bspBuildTargetCompile.key
          val _ = callback.appendExec(s"$command $targets", Some(r.id))

        case r: JsonRpcRequestMessage if r.method == "buildTarget/scalacOptions" =>
          val param = Converter.fromJson[ScalacOptionsParams](json(r)).get
          val targets = param.targets.map(_.uri).mkString(" ")
          val command = Keys.bspBuildTargetScalacOptions.key
          val _ = callback.appendExec(s"$command $targets", Some(r.id))
      },
      PartialFunction.empty
    )
  }

  private def json(r: JsonRpcRequestMessage): JValue =
    r.params.getOrElse(
      throw LangServerError(
        ErrorCodes.InvalidParams,
        s"param is expected on '${r.method}' method."
      )
    )

  private def buildTargetTask: Def.Initialize[Task[BuildTarget]] = Def.taskDyn {
    val buildTargetIdentifier = Keys.bspTargetIdentifier.value
    val thisProject = Keys.thisProject.value
    val thisProjectRef = Keys.thisProjectRef.value
    val thisConfig = Keys.configuration.value
    val scalaJars = Keys.scalaInstance.value.allJars.map(_.toURI.toString)
    val compileData = ScalaBuildTarget(
      scalaOrganization = scalaOrganization.value,
      scalaVersion = scalaVersion.value,
      scalaBinaryVersion = scalaBinaryVersion.value,
      platform = ScalaPlatform.JVM,
      jars = scalaJars.toVector
    )
    val configuration = Keys.configuration.value
    val displayName = configuration.name match {
      case "compile"  => thisProject.id
      case configName => s"${thisProject.id}-$configName"
    }
    val baseDirectory = Keys.baseDirectory.value.toURI
    val projectDependencies = for {
      (dep, configs) <- Keys.bspInternalDependencyConfigurations.value
      config <- configs
      if (dep != thisProjectRef || config != thisConfig.name) && bspTargetConfigs.contains(config)
    } yield Keys.bspTargetIdentifier.in(dep, ConfigKey(config))
    val capabilities = BuildTargetCapabilities(canCompile = true, canTest = false, canRun = false)
    val tags = BuildTargetTag.fromConfig(configuration.name)
    Def.task {
      BuildTarget(
        buildTargetIdentifier,
        Some(displayName),
        Some(baseDirectory),
        tags,
        capabilities,
        languageIds,
        projectDependencies.join.value.toVector,
        dataKind = Some("scala"),
        data = Some(Converter.toJsonUnsafe(compileData)),
      )
    }
  }

  private def scalacOptionsTask: Def.Initialize[Task[ScalacOptionsItem]] = Def.taskDyn {
    val target = Keys.bspTargetIdentifier.value
    val scalacOptions = Keys.scalacOptions.value
    val classDirectory = Keys.classDirectory.value
    val externalDependencyClasspath = Keys.externalDependencyClasspath.value

    val internalDependencyClasspath = for {
      (ref, configs) <- bspInternalDependencyConfigurations.value
      config <- configs
    } yield Keys.classDirectory.in(ref, ConfigKey(config))

    Def.task {
      val classpath = internalDependencyClasspath.join.value.distinct ++
        externalDependencyClasspath.map(_.data)
      ScalacOptionsItem(
        target,
        scalacOptions.toVector,
        classpath.map(_.toURI).toVector,
        classDirectory.toURI
      )
    }
  }

  private def dependencySourcesItemTask: Def.Initialize[Task[DependencySourcesItem]] = Def.task {
    val targetId = Keys.bspTargetIdentifier.value
    val updateReport = Keys.updateClassifiers.value
    val sources = for {
      configuration <- updateReport.configurations.view
      module <- configuration.modules.view
      (artifact, file) <- module.artifacts
      classifier <- artifact.classifier if classifier == "sources"
    } yield file.toURI
    DependencySourcesItem(targetId, sources.distinct.toVector)
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

  private def internalDependencyConfigurationsSetting = Def.settingDyn {
    val directDependencies = Keys.internalDependencyConfigurations.value
    val ref = Keys.thisProjectRef.value
    val thisConfig = Keys.configuration.value
    val transitiveDependencies = for {
      (dep, configs) <- directDependencies
      config <- configs if dep != ref || config != thisConfig.name
    } yield Keys.bspInternalDependencyConfigurations.in(dep, ConfigKey(config))
    Def.setting {
      val allDependencies = directDependencies ++ transitiveDependencies.join.value.flatten
      allDependencies
        .groupBy(_._1)
        .mapValues { deps =>
          deps.flatMap { case (_, configs) => configs }.toSet
        }
        .toSeq
    }
  }

  def scopeFilter(
      targets: Seq[BuildTargetIdentifier],
      workspace: Map[BuildTargetIdentifier, Scope]
  ): ScopeFilter = {
    ScopeFilter.in(targets.map(workspace))
  }

  def toId(ref: ProjectReference, config: Configuration): BuildTargetIdentifier =
    ref match {
      case ProjectRef(build, project) =>
        BuildTargetIdentifier(new URI(s"$build#$project/${config.id}"))
      case _ => sys.error(s"unexpected $ref")
    }
}
