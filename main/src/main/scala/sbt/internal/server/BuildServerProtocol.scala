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

import sbt.BasicCommandStrings.Shutdown
import sbt.BuildSyntax._
import sbt.Def._
import sbt.Keys._
import sbt.ScopeFilter.Make._
import sbt.SlashSyntax0._
import sbt.internal.bsp._
import sbt.internal.langserver.ErrorCodes
import sbt.internal.protocol.JsonRpcRequestMessage
import sbt.librarymanagement.Configuration
import sbt.util.Logger
import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import sjsonnew.support.scalajson.unsafe.Converter

import scala.util.control.NonFatal

object BuildServerProtocol {
  import sbt.internal.bsp.codec.JsonProtocol._
  private val capabilities = BuildServerCapabilities(
    CompileProvider(BuildServerConnection.languages),
    dependencySourcesProvider = true
  )

  lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
    bspWorkspace := bspWorkspaceSetting.value,
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

  // This will be scoped to Compile, Test, IntegrationTest etc
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

  def handler(
      sbtVersion: String,
      semanticdbEnabled: Boolean,
      semanticdbVersion: String
  ): ServerHandler = ServerHandler { callback =>
    ServerIntent(
      onRequest = {
        case r: JsonRpcRequestMessage if r.method == "build/initialize" =>
          val params = Converter.fromJson[InitializeBuildParams](json(r)).get
          checkMetalsCompatibility(semanticdbEnabled, semanticdbVersion, params, callback.log)

          val response = InitializeBuildResult(
            "sbt",
            sbtVersion,
            BuildServerConnection.bspVersion,
            capabilities,
            None
          )
          callback.jsonRpcRespond(response, Some(r.id)); ()

        case r: JsonRpcRequestMessage if r.method == "workspace/buildTargets" =>
          val _ = callback.appendExec(Keys.bspWorkspaceBuildTargets.key.toString, Some(r.id))

        case r: JsonRpcRequestMessage if r.method == "build/shutdown" =>
          ()

        case r: JsonRpcRequestMessage if r.method == "build/exit" =>
          val _ = callback.appendExec(Shutdown, Some(r.id))

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
      onResponse = PartialFunction.empty,
      onNotification = PartialFunction.empty,
    )
  }

  private def checkMetalsCompatibility(
      semanticdbEnabled: Boolean,
      semanticdbVersion: String,
      params: InitializeBuildParams,
      log: Logger
  ): Unit = {
    for {
      data <- params.data
      // try parse metadata as MetalsMetadata
      metalsMetadata <- Converter.fromJson[MetalsMetadata](data).toOption
    } {
      if (!semanticdbEnabled) {
        log.warn(s"${params.displayName} requires the semanticdb compiler plugin")
        log.warn(
          s"consider setting 'Global / semanticdbEnabled := true' in your global sbt settings ($$HOME/.sbt/1.0)"
        )
      }

      for {
        requiredVersion <- SemanticVersion.tryParse(metalsMetadata.semanticdbVersion)
        currentVersion <- SemanticVersion.tryParse(semanticdbVersion)
        if requiredVersion > currentVersion
      } {
        log.warn(
          s"${params.displayName} requires semanticdb version ${metalsMetadata.semanticdbVersion}, current version is $semanticdbVersion"
        )
        log.warn(
          s"""consider setting 'Global / semanticdbVersion := "${metalsMetadata.semanticdbVersion}"' in your global sbt settings ($$HOME/.sbt/1.0)"""
        )
      }
    }
  }

  private def json(r: JsonRpcRequestMessage): JValue =
    r.params.getOrElse(
      throw LangServerError(
        ErrorCodes.InvalidParams,
        s"param is expected on '${r.method}' method."
      )
    )

  private def bspWorkspaceSetting: Def.Initialize[Map[BuildTargetIdentifier, Scope]] =
    Def.settingDyn {
      val loadedBuild = Keys.loadedBuild.value

      // list all defined scopes for setting bspTargetIdentifier for all projects
      val scopes: Seq[Scope] = for {
        (ref, project) <- loadedBuild.allProjectRefs
        setting <- project.settings
        if setting.key.key.label == Keys.bspTargetIdentifier.key.label
      } yield Scope.replaceThis(Scope.Global.in(ref))(setting.key.scope)

      Def.setting {
        val targetIds = scopes
          .map(_ / Keys.bspTargetIdentifier)
          .join
          .value
        targetIds.zip(scopes).toMap
      }
    }

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
    val displayName = BuildTargetName.fromScope(thisProject.id, configuration.name)
    val baseDirectory = Keys.baseDirectory.value.toURI
    val projectDependencies = for {
      (dep, configs) <- Keys.bspInternalDependencyConfigurations.value
      config <- configs
      if dep != thisProjectRef || config.name != thisConfig.name
    } yield Keys.bspTargetIdentifier.in(dep, config)
    val capabilities = BuildTargetCapabilities(canCompile = true, canTest = false, canRun = false)
    val tags = BuildTargetTag.fromConfig(configuration.name)
    Def.task {
      BuildTarget(
        buildTargetIdentifier,
        Some(displayName),
        Some(baseDirectory),
        tags,
        capabilities,
        BuildServerConnection.languages,
        projectDependencies.join.value.distinct.toVector,
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
    } yield Keys.classDirectory.in(ref, config)

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
    val directDependencies = Keys.internalDependencyConfigurations.value.map {
      case (project, rawConfigs) =>
        val configs = rawConfigs.flatMap(_.split(",")).map(name => ConfigKey(name.trim))
        (project, configs)
    }
    val ref = Keys.thisProjectRef.value
    val thisConfig = Keys.configuration.value
    val transitiveDependencies = for {
      (dep, configs) <- directDependencies
      config <- configs if dep != ref || config.name != thisConfig.name
    } yield Keys.bspInternalDependencyConfigurations.in(dep, config)
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

  private def toId(ref: ProjectReference, config: Configuration): BuildTargetIdentifier =
    ref match {
      case ProjectRef(build, project) =>
        BuildTargetIdentifier(new URI(s"$build#$project/${config.id}"))
      case _ => sys.error(s"unexpected $ref")
    }

  private case class SemanticVersion(major: Int, minor: Int) extends Ordered[SemanticVersion] {
    override def compare(that: SemanticVersion): Int = {
      if (that.major != major) major.compare(that.major)
      else minor.compare(minor)
    }
  }

  private object SemanticVersion {
    def tryParse(versionStr: String): Option[SemanticVersion] = {
      try {
        val parts = versionStr.split('.')
        Some(SemanticVersion(parts(0).toInt, parts(1).toInt))
      } catch {
        case NonFatal(_) => None
      }
    }
  }
}
