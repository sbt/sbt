/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package server

import java.net.URI
import sbt.BuildPaths.{ configurationSources, projectStandard }
import sbt.BuildSyntax._
import sbt.Def._
import sbt.Keys._
import sbt.Project._
import sbt.ScopeFilter.Make._
import sbt.Scoped.richTaskSeq
import sbt.SlashSyntax0._
import sbt.StandardMain.exchange
import sbt.internal.bsp._
import sbt.internal.langserver.ErrorCodes
import sbt.internal.protocol.JsonRpcRequestMessage
import sbt.internal.util.{ Attributed, ErrorHandling }
import sbt.internal.util.complete.{ Parser, Parsers }
import sbt.librarymanagement.CrossVersion.binaryScalaVersion
import sbt.librarymanagement.{ Configuration, ScalaArtifacts }
import sbt.std.TaskExtra
import sbt.util.Logger
import sjsonnew.shaded.scalajson.ast.unsafe.{ JNull, JValue }
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter, Parser => JsonParser }
import xsbti.CompileFailed

import java.nio.file.Path
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable

// import scala.annotation.nowarn
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }
import scala.annotation.nowarn
import sbt.testing.Framework

object BuildServerProtocol {
  import sbt.internal.bsp.codec.JsonProtocol._

  private val capabilities = BuildServerCapabilities(
    CompileProvider(BuildServerConnection.languages),
    TestProvider(BuildServerConnection.languages),
    RunProvider(BuildServerConnection.languages),
    dependencySourcesProvider = true,
    resourcesProvider = true,
    outputPathsProvider = true,
    canReload = true,
    jvmRunEnvironmentProvider = true,
    jvmTestEnvironmentProvider = true,
  )

  private val bspReload = "bspReload"

  lazy val commands: Seq[Command] = Seq(
    Command.single(bspReload) { (state, reqId) =>
      try {
        val newState = BuiltinCommands.doLoadProject(state, Project.LoadAction.Current)
        exchange.respondEvent(JNull, Some(reqId), state.source)
        newState
      } catch {
        case NonFatal(e) =>
          val msg = ErrorHandling.reducedToString(e)
          exchange.respondError(ErrorCodes.InternalError, msg, Some(reqId), state.source)
          state.fail
      }
    }
  )

  lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
    bspConfig := {
      if (bspEnabled.value) {
        BuildServerConnection.writeConnectionFile(
          sbtVersion.value,
          (ThisBuild / baseDirectory).value
        )
      } else {
        val logger = streams.value.log
        logger.warn("BSP is disabled for this build")
        logger.info("add 'Global / bspEnabled := true' to enable BSP")
      }
    },
    bspEnabled := true,
    bspSbtEnabled := true,
    bspFullWorkspace := bspFullWorkspaceSetting.value,
    bspWorkspace := bspFullWorkspace.value.scopes,
    bspWorkspaceBuildTargets := Def.taskDyn {
      val workspace = Keys.bspFullWorkspace.value
      val state = Keys.state.value
      val allTargets = ScopeFilter.in(workspace.scopes.values.toSeq)
      val sbtTargets = workspace.builds.map {
        case (buildTargetIdentifier, loadedBuildUnit) =>
          val buildFor = workspace.buildToScope.getOrElse(buildTargetIdentifier, Nil)
          sbtBuildTarget(loadedBuildUnit, buildTargetIdentifier, buildFor).result
      }.toList
      Def.task {
        val buildTargets = Keys.bspBuildTarget.result.all(allTargets).value
        val successfulBuildTargets = anyOrThrow(buildTargets ++ sbtTargets.join.value)
        state.respondEvent(WorkspaceBuildTargetsResult(successfulBuildTargets.toVector))
        successfulBuildTargets
      }
    }.value,
    // https://github.com/build-server-protocol/build-server-protocol/blob/master/docs/specification.md#build-target-sources-request
    bspBuildTargetSources := bspInputTask { (state, _, workspace, filter) =>
      // run the worker task concurrently
      Def.task {
        val items = bspBuildTargetSourcesItem.result.all(filter).value
        val buildItems = workspace.builds.map {
          case (id, loadedBuildUnit) =>
            val base = loadedBuildUnit.localBase
            val sbtFiles = configurationSources(base)
            val pluginData = loadedBuildUnit.unit.plugins.pluginData
            val dirs = pluginData.unmanagedSourceDirectories
            val sourceFiles = getStandaloneSourceFiles(pluginData.unmanagedSources, dirs)
            val managedDirs = pluginData.managedSourceDirectories
            val managedSourceFiles =
              getStandaloneSourceFiles(pluginData.managedSources, managedDirs)
            val items =
              dirs.map(toSourceItem(SourceItemKind.Directory, generated = false)) ++
                sourceFiles.map(toSourceItem(SourceItemKind.File, generated = false)) ++
                managedDirs.map(toSourceItem(SourceItemKind.Directory, generated = true)) ++
                managedSourceFiles.map(toSourceItem(SourceItemKind.File, generated = true)) ++
                sbtFiles.map(toSourceItem(SourceItemKind.File, generated = false))
            Value(SourcesItem(id, items.toVector))
        }
        val successfulItems = anyOrThrow(items ++ buildItems)
        val result = SourcesResult(successfulItems.toVector)
        state.respondEvent(result)
      }
    }.evaluated,
    bspBuildTargetSources / aggregate := false,
    bspBuildTargetResources := bspInputTask { (state, _, workspace, filter) =>
      workspace.warnIfBuildsNonEmpty(Method.Resources, state.log)
      // run the worker task concurrently
      Def.task {
        val items = bspBuildTargetResourcesItem.result.all(filter).value
        val successfulItems = anyOrThrow(items)
        val result = ResourcesResult(successfulItems.toVector)
        state.respondEvent(result)
      }
    }.evaluated,
    bspBuildTargetResources / aggregate := false,
    bspBuildTargetDependencySources := bspInputTask { (state, _, workspace, filter) =>
      // run the worker task concurrently
      Def.task {
        import sbt.internal.bsp.codec.JsonProtocol._
        val items = bspBuildTargetDependencySourcesItem.result.all(filter).value
        val successfulItems = anyOrThrow(items)
        val result = DependencySourcesResult(successfulItems.toVector)
        state.respondEvent(result)
      }
    }.evaluated,
    bspBuildTargetDependencySources / aggregate := false,
    bspBuildTargetOutputPaths := bspInputTask { (state, _, workspace, filter) =>
      Def.task {
        import sbt.internal.bsp.codec.JsonProtocol._
        val items = bspBuildTargetOutputPathsItem.result.all(filter).value
        val successfulItems = anyOrThrow(items)
        val result = OutputPathsResult(successfulItems.toVector)
        state.respondEvent(result)
      }
    }.evaluated,
    bspBuildTargetOutputPaths / aggregate := false,
    bspBuildTargetCompile := bspInputTask { (state, _, workspace, filter) =>
      workspace.warnIfBuildsNonEmpty(Method.Compile, state.log)
      Def.task {
        val statusCodes = Keys.bspBuildTargetCompileItem.result.all(filter).value
        val aggregatedStatusCode = allOrThrow(statusCodes) match {
          case Seq() => StatusCode.Success
          case codes => codes.max
        }
        state.respondEvent(BspCompileResult(None, aggregatedStatusCode))
      }
    }.evaluated,
    bspBuildTargetCompile / aggregate := false,
    bspBuildTargetTest := bspTestTask.evaluated,
    bspBuildTargetTest / aggregate := false,
    bspBuildTargetCleanCache := bspInputTask { (state, targets, workspace, filter) =>
      workspace.warnIfBuildsNonEmpty(Method.CleanCache, state.log)
      Def.task {
        val results = Keys.clean.result.all(filter).value
        val successes = anyOrThrow(results).size

        // When asking to Rebuild Project, IntelliJ sends the root build as an additional target, however it is
        // not returned as part of the results. In this case, there's 1 build entry in the workspace, and we're
        // checking that the executed results plus this entry is equal to the total number of targets.
        // When rebuilding a single module, the root build isn't sent, just the requested targets.
        val cleaned = successes + workspace.builds.size == targets.size
        state.respondEvent(CleanCacheResult(None, cleaned))
      }
    }.evaluated,
    bspBuildTargetCleanCache / aggregate := false,
    bspBuildTargetScalacOptions := bspInputTask { (state, _, workspace, filter) =>
      val builds = workspace.builds
      Def.task {
        val items = bspBuildTargetScalacOptionsItem.result.all(filter).value
        val appProvider = appConfiguration.value.provider()
        val sbtJars = appProvider.mainClasspath()
        val buildItems = builds.map {
          build =>
            val plugins: LoadedPlugins = build._2.unit.plugins
            val scalacOptions = plugins.pluginData.scalacOptions
            val pluginClassPath = plugins.classpath
            val classpath = (pluginClassPath ++ sbtJars).map(_.toURI).toVector
            val item = ScalacOptionsItem(
              build._1,
              scalacOptions.toVector,
              classpath,
              new File(build._2.localBase, "project/target").toURI
            )
            Value(item)
        }
        val successfulItems = anyOrThrow(items ++ buildItems)
        val result = ScalacOptionsResult(successfulItems.toVector)
        state.respondEvent(result)
      }
    }.evaluated,
    bspBuildTargetScalacOptions / aggregate := false,
    bspScalaTestClasses := bspInputTask { (state, _, workspace, filter) =>
      workspace.warnIfBuildsNonEmpty(Method.ScalaTestClasses, state.log)
      Def.task {
        val items = bspScalaTestClassesItem.result.all(filter).value
        val successfulItems = anyOrThrow(items).flatten.toVector
        val result = ScalaTestClassesResult(successfulItems.toVector, None)
        state.respondEvent(result)
      }
    }.evaluated,
    bspScalaMainClasses := bspInputTask { (state, _, workspace, filter) =>
      workspace.warnIfBuildsNonEmpty(Method.ScalaMainClasses, state.log)
      Def.task {
        val items = bspScalaMainClassesItem.result.all(filter).value
        val successfulItems = anyOrThrow(items)
        val result = ScalaMainClassesResult(successfulItems.toVector, None)
        state.respondEvent(result)
      }
    }.evaluated,
    bspScalaMainClasses / aggregate := false
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
      val sourceFiles = getStandaloneSourceFiles(unmanagedSources.value, dirs)
      val managedDirs = managedSourceDirectories.value
      val managedSourceFiles = getStandaloneSourceFiles(managedSources.value, managedDirs)
      val items = dirs.map(toSourceItem(SourceItemKind.Directory, generated = false)) ++
        sourceFiles.map(toSourceItem(SourceItemKind.File, generated = false)) ++
        managedDirs.map(toSourceItem(SourceItemKind.Directory, generated = true)) ++
        managedSourceFiles.map(toSourceItem(SourceItemKind.File, generated = true))
      SourcesItem(id, items.toVector)
    },
    bspBuildTargetResourcesItem := {
      val id = bspTargetIdentifier.value
      // Trigger resource generation
      val _ = managedResources.value
      val uris = resourceDirectories.value.toVector
        .map { resourceDirectory =>
          // Add any missing ending slash to the URI to explicitly mark it as a directory
          // See https://github.com/build-server-protocol/build-server-protocol/issues/181 for more information
          URI.create(s"${resourceDirectory.toURI.toString.replaceAll("([^/])$", "$1/")}")
        }
      ResourcesItem(id, uris)
    },
    bspBuildTargetDependencySourcesItem := dependencySourcesItemTask.value,
    bspBuildTargetOutputPathsItem := {
      val id = bspTargetIdentifier.value
      OutputPathsItem(id, Vector(OutputPathItem(target.value.toURI, OutputPathItemKind.Directory)))
    },
    bspBuildTargetCompileItem := bspCompileTask.value,
    bspBuildTargetRun := bspRunTask.evaluated,
    bspBuildTargetScalacOptionsItem := scalacOptionsTask.value,
    bspBuildTargetJVMRunEnvironment := bspInputTask { (state, _, _, filter) =>
      Def.task {
        val items = bspBuildTargetJvmEnvironmentItem.result.all(filter).value
        val successfulItems = anyOrThrow(items)
        val result = JvmRunEnvironmentResult(successfulItems.toVector, None)
        state.respondEvent(result)
      }
    }.evaluated,
    bspBuildTargetJVMTestEnvironment := bspInputTask { (state, _, _, filter) =>
      Def.task {
        val items = bspBuildTargetJvmEnvironmentItem.result.all(filter).value
        val successfulItems = anyOrThrow(items)
        val result = JvmTestEnvironmentResult(successfulItems.toVector, None)
        state.respondEvent(result)
      }
    }.evaluated,
    bspBuildTargetJvmEnvironmentItem := jvmEnvironmentItem().value,
    bspInternalDependencyConfigurations := internalDependencyConfigurationsSetting.value,
    bspScalaTestClassesItem := scalaTestClassesTask.value,
    bspScalaMainClassesItem := scalaMainClassesTask.value,
    Keys.compile / bspReporter := {
      val targetId = bspTargetIdentifier.value
      val bspCompileStateInstance = bspCompileState.value
      val converter = fileConverter.value
      val underlying = (Keys.compile / compilerReporter).value
      val logger = streams.value.log
      val meta = isMetaBuild.value
      if (bspEnabled.value) {
        new BuildServerReporterImpl(
          targetId,
          bspCompileStateInstance,
          converter,
          meta,
          logger,
          underlying
        )
      } else {
        new BuildServerForwarder(meta, logger, underlying)
      }
    }
  )
  private object Method {
    final val Initialize = "build/initialize"
    final val BuildTargets = "workspace/buildTargets"
    final val Reload = "workspace/reload"
    final val Shutdown = "build/shutdown"
    final val Sources = "buildTarget/sources"
    final val Resources = "buildTarget/resources"
    final val OutputPaths = "buildTarget/outputPaths"
    final val DependencySources = "buildTarget/dependencySources"
    final val Compile = "buildTarget/compile"
    final val Test = "buildTarget/test"
    final val Run = "buildTarget/run"
    final val CleanCache = "buildTarget/cleanCache"
    final val ScalacOptions = "buildTarget/scalacOptions"
    final val JvmRunEnvironment = "buildTarget/jvmRunEnvironment"
    final val JvmTestEnvironment = "buildTarget/jvmTestEnvironment"
    final val ScalaTestClasses = "buildTarget/scalaTestClasses"
    final val ScalaMainClasses = "buildTarget/scalaMainClasses"
    final val Exit = "build/exit"
  }
  identity(Method) // silence spurious "private object Method in object BuildServerProtocol is never used" warning!

  def handler(
      loadedBuild: LoadedBuild,
      workspace: BspFullWorkspace,
      sbtVersion: String,
      semanticdbEnabled: Boolean,
      semanticdbVersion: String
  ): ServerHandler = {
    val configurationMap: Map[ConfigKey, Configuration] =
      loadedBuild.allProjectRefs
        .flatMap { case (_, p) => p.configurations }
        .distinct
        .map(c => ConfigKey(c.name) -> c)
        .toMap
    ServerHandler { callback =>
      ServerIntent(
        onRequest = {
          case r if r.method == Method.Initialize =>
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

          case r if r.method == Method.BuildTargets =>
            val _ = callback.appendExec(Keys.bspWorkspaceBuildTargets.key.toString, Some(r.id))

          case r if r.method == Method.Reload =>
            val _ = callback.appendExec(s"$bspReload ${r.id}", Some(r.id))

          case r if r.method == Method.Shutdown =>
            callback.jsonRpcRespond(JNull, Some(r.id))

          case r if r.method == Method.Sources =>
            val param = Converter.fromJson[SourcesParams](json(r)).get
            val targets = param.targets.map(_.uri).mkString(" ")
            val command = Keys.bspBuildTargetSources.key
            val _ = callback.appendExec(s"$command $targets", Some(r.id))

          case r if r.method == Method.DependencySources =>
            val param = Converter.fromJson[DependencySourcesParams](json(r)).get
            val targets = param.targets.map(_.uri).mkString(" ")
            val command = Keys.bspBuildTargetDependencySources.key
            val _ = callback.appendExec(s"$command $targets", Some(r.id))

          case r if r.method == Method.Compile =>
            val param = Converter.fromJson[CompileParams](json(r)).get
            val targets = param.targets.map(_.uri).mkString(" ")
            val command = Keys.bspBuildTargetCompile.key
            val _ = callback.appendExec(s"$command $targets", Some(r.id))

          case r: JsonRpcRequestMessage if r.method == Method.Test =>
            val task = bspBuildTargetTest.key
            val paramStr = CompactPrinter(json(r))
            val _ = callback.appendExec(s"$task $paramStr", Some(r.id))

          case r if r.method == Method.Run =>
            val paramJson = json(r)
            val param = Converter.fromJson[RunParams](json(r)).get
            val scope = workspace.scopes.getOrElse(
              param.target,
              throw LangServerError(
                ErrorCodes.InvalidParams,
                s"'${param.target}' is not a valid build target identifier"
              )
            )
            val project = scope.project.toOption.get.asInstanceOf[ProjectRef].project
            val config = configurationMap(scope.config.toOption.get).id
            val task = bspBuildTargetRun.key
            val paramStr = CompactPrinter(paramJson)
            val _ = callback.appendExec(
              s"$project / $config / $task $paramStr",
              Some(r.id)
            )

          case r if r.method == Method.CleanCache =>
            val param = Converter.fromJson[CleanCacheParams](json(r)).get
            val targets = param.targets.map(_.uri).mkString(" ")
            val command = Keys.bspBuildTargetCleanCache.key
            val _ = callback.appendExec(s"$command $targets", Some(r.id))

          case r if r.method == Method.ScalacOptions =>
            val param = Converter.fromJson[ScalacOptionsParams](json(r)).get
            val targets = param.targets.map(_.uri).mkString(" ")
            val command = Keys.bspBuildTargetScalacOptions.key
            val _ = callback.appendExec(s"$command $targets", Some(r.id))

          case r if r.method == Method.JvmRunEnvironment =>
            val param = Converter.fromJson[JvmRunEnvironmentParams](json(r)).get
            val targets = param.targets.map(_.uri).mkString(" ")
            val command = Keys.bspBuildTargetJVMRunEnvironment.key
            val _ = callback.appendExec(s"$command $targets", Some(r.id))

          case r if r.method == Method.JvmTestEnvironment =>
            val param = Converter.fromJson[JvmTestEnvironmentParams](json(r)).get
            val targets = param.targets.map(_.uri).mkString(" ")
            val command = Keys.bspBuildTargetJVMTestEnvironment.key
            val _ = callback.appendExec(s"$command $targets", Some(r.id))

          case r if r.method == Method.ScalaTestClasses =>
            val param = Converter.fromJson[ScalaTestClassesParams](json(r)).get
            val targets = param.targets.map(_.uri).mkString(" ")
            val command = Keys.bspScalaTestClasses.key
            val _ = callback.appendExec(s"$command $targets", Some(r.id))

          case r if r.method == Method.ScalaMainClasses =>
            val param = Converter.fromJson[ScalaMainClassesParams](json(r)).get
            val targets = param.targets.map(_.uri).mkString(" ")
            val command = Keys.bspScalaMainClasses.key
            val _ = callback.appendExec(s"$command $targets", Some(r.id))

          case r if r.method == Method.Resources =>
            val param = Converter.fromJson[ResourcesParams](json(r)).get
            val targets = param.targets.map(_.uri).mkString(" ")
            val command = Keys.bspBuildTargetResources.key
            val _ = callback.appendExec(s"$command $targets", Some(r.id))

          case r if r.method == Method.OutputPaths =>
            val param = Converter.fromJson[OutputPathsParams](json(r)).get
            val targets = param.targets.map(_.uri).mkString(" ")
            val command = Keys.bspBuildTargetOutputPaths.key
            val _ = callback.appendExec(s"$command $targets", Some(r.id))
        },
        onResponse = PartialFunction.empty,
        onNotification = {
          case r if r.method == Method.Exit =>
            val _ = callback.appendExec(BasicCommandStrings.TerminateAction, None)
        },
      )
    }
  }

  private def getStandaloneSourceFiles(
      sourceFiles: Seq[File],
      sourceDirs: Seq[File]
  ): Seq[File] = {
    sourceFiles.filterNot { f =>
      sourceDirs.exists(dir => f.toPath.startsWith(dir.toPath))
    }
  }

  private def toSourceItem(itemKind: Int, generated: Boolean)(file: File): SourceItem =
    SourceItem(file.toURI, itemKind, generated)

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
      if (metalsMetadata.semanticdbVersion.nonEmpty && !semanticdbEnabled) {
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

  @nowarn
  private def bspFullWorkspaceSetting: Def.Initialize[BspFullWorkspace] =
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
        val bspEnabled = scopes
          .map(_ / Keys.bspEnabled)
          .join
          .value
        val buildsMap =
          mutable.HashMap[BuildTargetIdentifier, mutable.ListBuffer[BuildTargetIdentifier]]()

        val scopeMap = for {
          (targetId, scope, bspEnabled) <- (targetIds, scopes, bspEnabled).zipped
          if bspEnabled
        } yield {
          scope.project.toOption match {
            case Some(ProjectRef(buildUri, _)) =>
              val loadedBuildUnit = loadedBuild.units(buildUri)
              buildsMap.getOrElseUpdate(
                toSbtTargetId(loadedBuildUnit),
                new mutable.ListBuffer
              ) += targetId
          }
          targetId -> scope
        }
        val buildMap = if (bspSbtEnabled.value) {
          for (loadedBuildUnit <- loadedBuild.units.values) yield {
            val rootProjectId = loadedBuildUnit.root
            toSbtTargetId(loadedBuildUnit) -> loadedBuildUnit
          }
        } else {
          Nil
        }
        BspFullWorkspace(scopeMap.toMap, buildMap.toMap, buildsMap.mapValues(_.result()).toMap)
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
    } yield (dep / config / Keys.bspTargetIdentifier)
    val capabilities = BuildTargetCapabilities(canCompile = true, canTest = true, canRun = true)
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

  private def sbtBuildTarget(
      loadedUnit: LoadedBuildUnit,
      buildTargetIdentifier: BuildTargetIdentifier,
      buildFor: Seq[BuildTargetIdentifier]
  ): Def.Initialize[Task[BuildTarget]] = Def.task {
    val scalaProvider = appConfiguration.value.provider().scalaProvider()
    appConfiguration.value.provider().mainClasspath()
    val scalaJars = scalaProvider.jars()
    val compileData = ScalaBuildTarget(
      scalaOrganization = ScalaArtifacts.Organization,
      scalaVersion = scalaProvider.version(),
      scalaBinaryVersion = binaryScalaVersion(scalaProvider.version()),
      platform = ScalaPlatform.JVM,
      jars = scalaJars.toVector.map(_.toURI.toString)
    )
    val sbtVersionValue = sbtVersion.value
    val sbtData = SbtBuildTarget(
      sbtVersionValue,
      loadedUnit.imports.toVector,
      compileData,
      None,
      buildFor.toVector
    )

    BuildTarget(
      buildTargetIdentifier,
      toSbtTargetIdName(loadedUnit),
      projectStandard(loadedUnit.unit.localBase).toURI,
      Vector(),
      BuildTargetCapabilities(canCompile = false, canTest = false, canRun = false),
      BuildServerConnection.languages,
      Vector(),
      "sbt",
      data = Converter.toJsonUnsafe(sbtData),
    )
  }

  private def bspInputTask[T](
      taskImpl: (
          State,
          Seq[BuildTargetIdentifier],
          BspFullWorkspace,
          ScopeFilter
      ) => Def.Initialize[Task[T]]
  ): Def.Initialize[InputTask[T]] =
    Def.inputTaskDyn {
      val s = state.value
      val targets = spaceDelimited().parsed.map(uri => BuildTargetIdentifier(URI.create(uri)))
      val workspace: BspFullWorkspace = bspFullWorkspace.value.filter(targets)
      val filter = ScopeFilter.in(workspace.scopes.values.toList)
      taskImpl(s, targets, workspace, filter)
    }

  private def jvmEnvironmentItem(): Initialize[Task[JvmEnvironmentItem]] = Def.task {
    val target = Keys.bspTargetIdentifier.value
    val classpath = Keys.fullClasspath.value.map(_.data.toURI).toVector
    val jvmOptions = Keys.javaOptions.value.toVector
    val baseDir = Keys.baseDirectory.value.toURI().toString()
    val env = envVars.value

    JvmEnvironmentItem(
      target,
      classpath,
      jvmOptions,
      baseDir,
      env
    )
  }

  private def scalacOptionsTask: Def.Initialize[Task[ScalacOptionsItem]] = Def.taskDyn {
    val target = Keys.bspTargetIdentifier.value
    val scalacOptions = Keys.scalacOptions.value
    val classDirectory = Keys.classDirectory.value
    val externalDependencyClasspath = Keys.externalDependencyClasspath.value

    val internalDependencyClasspath = for {
      (ref, configs) <- bspInternalDependencyConfigurations.value
      config <- configs
    } yield ref / config / Keys.classDirectory

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
    DependencySourcesItem(targetId, sources.toVector.distinct)
  }

  private def bspCompileState: Initialize[BuildServerProtocol.BspCompileState] = Def.setting {
    new BuildServerProtocol.BspCompileState()
  }

  private def bspCompileTask: Def.Initialize[Task[Int]] = Def.task {
    Keys.compile.result.value match {
      case Value(_) => StatusCode.Success
      case Inc(cause) =>
        cause.getCause match {
          case _: CompileFailed        => StatusCode.Error
          case _: InterruptedException => StatusCode.Cancelled
          case err                     => throw cause
        }
    }
  }

  private val jsonParser: Parser[Try[JValue]] = (Parsers.any *)
    .map(_.mkString)
    .map(JsonParser.parseFromString)

  private def bspRunTask: Def.Initialize[InputTask[Unit]] = Def.inputTaskDyn {
    val runParams = jsonParser
      .map(_.flatMap(json => Converter.fromJson[RunParams](json)))
      .parsed
      .get
    val defaultClass = Keys.mainClass.value
    val defaultJvmOptions = Keys.javaOptions.value

    val mainClass = runParams.dataKind match {
      case Some("scala-main-class") =>
        val data = runParams.data.getOrElse(JNull)
        Converter.fromJson[ScalaMainClass](data) match {
          case Failure(e) =>
            throw LangServerError(
              ErrorCodes.ParseError,
              e.getMessage
            )
          case Success(value) =>
            value.withEnvironmentVariables(
              envVars.value.map { case (k, v) => s"$k=$v" }.toVector ++ value.environmentVariables
            )
        }

      case Some(dataKind) =>
        throw LangServerError(
          ErrorCodes.InvalidParams,
          s"Unexpected data of kind '$dataKind', 'scala-main-class' is expected"
        )

      case None =>
        ScalaMainClass(
          defaultClass.getOrElse(
            throw LangServerError(
              ErrorCodes.InvalidParams,
              "No default main class is defined"
            )
          ),
          runParams.arguments,
          defaultJvmOptions.toVector,
          envVars.value.map { case (k, v) => s"$k=$v" }.toVector
        )
    }

    runMainClassTask(mainClass, runParams.originId)
  }

  private def bspTestTask: Def.Initialize[InputTask[Unit]] = Def.inputTaskDyn {
    val testParams = jsonParser
      .map(_.flatMap(json => Converter.fromJson[TestParams](json)))
      .parsed
      .get
    val workspace = bspFullWorkspace.value

    val resultTask: Def.Initialize[Task[Result[Seq[Unit]]]] = testParams.dataKind match {
      case Some("scala-test") =>
        val data = testParams.data.getOrElse(JNull)
        val items = Converter.fromJson[ScalaTestParams](data) match {
          case Failure(e) =>
            throw LangServerError(ErrorCodes.ParseError, e.getMessage)
          case Success(value) => value.testClasses
        }
        val testTasks: Seq[Def.Initialize[Task[Unit]]] = items.map { item =>
          val scope = workspace.scopes(item.target)
          item.classes.toList match {
            case Nil => Def.task(())
            case classes =>
              (scope / testOnly).toTask(" " + classes.mkString(" "))
          }
        }
        testTasks.joinWith(ts => TaskExtra.joinTasks(ts).join).result

      case Some(dataKind) =>
        throw LangServerError(
          ErrorCodes.InvalidParams,
          s"Unexpected data of kind '$dataKind', 'scala-main-class' is expected"
        )

      case None =>
        // run allTests in testParams.targets
        val filter = ScopeFilter.in(testParams.targets.map(workspace.scopes))
        test.all(filter).result
    }

    Def.task {
      val state = Keys.state.value
      val statusCode = resultTask.value match {
        case Value(_) => StatusCode.Success
        case Inc(_)   => StatusCode.Error
      }
      val _ = state.respondEvent(TestResult(testParams.originId, statusCode))
    }
  }

  private def runMainClassTask(mainClass: ScalaMainClass, originId: Option[String]) = Def.task {
    val state = Keys.state.value
    val logger = Keys.streams.value.log
    val classpath = Attributed.data(fullClasspath.value)
    val forkOpts = ForkOptions(
      javaHome = javaHome.value,
      outputStrategy = outputStrategy.value,
      // bootJars is empty by default because only jars on the user's classpath should be on the boot classpath
      bootJars = Vector(),
      workingDirectory = Some(baseDirectory.value),
      runJVMOptions = mainClass.jvmOptions,
      connectInput = connectInput.value,
      envVars = mainClass.environmentVariables
        .flatMap(_.split("=", 2).toList match {
          case key :: value :: Nil => Some(key -> value)
          case _                   => None
        })
        .toMap
    )
    val runner = new ForkRun(forkOpts)
    val statusCode = runner
      .run(mainClass.`class`, classpath, mainClass.arguments, logger)
      .fold(
        _ => StatusCode.Error,
        _ => StatusCode.Success
      )
    state.respondEvent(RunResult(originId, statusCode))
  }

  @nowarn
  private def internalDependencyConfigurationsSetting = Def.settingDyn {
    val allScopes = bspFullWorkspace.value.scopes.map { case (_, scope) => scope }.toSet
    val directDependencies = Keys.internalDependencyConfigurations.value
      .map {
        case (project, rawConfigs) =>
          val configs = rawConfigs
            .flatMap(_.split(","))
            .map(name => ConfigKey(name.trim))
            .filter { config =>
              val scope = Scope.Global.in(project, config)
              allScopes.contains(scope)
            }
          (project, configs)
      }
      .filter {
        case (_, configs) => configs.nonEmpty
      }
    val ref = Keys.thisProjectRef.value
    val thisConfig = Keys.configuration.value
    val transitiveDependencies = for {
      (dep, configs) <- directDependencies
      config <- configs if dep != ref || config.name != thisConfig.name
    } yield dep / config / Keys.bspInternalDependencyConfigurations
    Def.setting {
      val allDependencies = directDependencies ++
        transitiveDependencies.join.value.flatten
      allDependencies
        .groupBy(_._1)
        .mapValues { deps =>
          deps.flatMap { case (_, configs) => configs }.toSet
        }
        .toSeq
    }
  }

  private def scalaTestClassesTask: Initialize[Task[Seq[ScalaTestClassesItem]]] = Def.task {
    Keys.definedTests.?.value match {
      case None => Vector.empty
      case Some(definitions) =>
        val frameworks: Seq[Framework] = Keys.loadedTestFrameworks.?.value
          .map(_.values.toSeq)
          .getOrElse(Seq.empty)

        val grouped = TestFramework.testMap(frameworks, definitions)

        grouped.map {
          case (framework, definitions) =>
            ScalaTestClassesItem(
              bspTargetIdentifier.value,
              definitions.map(_.name).toVector,
              framework.name()
            )
        }.toSeq
    }
  }

  private def scalaMainClassesTask: Initialize[Task[ScalaMainClassesItem]] = Def.task {
    val jvmOptions = Keys.javaOptions.value.toVector
    val mainClasses = Keys.discoveredMainClasses.value.map(
      ScalaMainClass(
        _,
        Vector(),
        jvmOptions,
        envVars.value.map { case (k, v) => s"$k=$v" }.toVector
      )
    )
    ScalaMainClassesItem(
      bspTargetIdentifier.value,
      mainClasses.toVector
    )
  }

  // naming convention still seems like the only reliable way to get IntelliJ to import this correctly
  // https://github.com/JetBrains/intellij-scala/blob/a54c2a7c157236f35957049cbfd8c10587c9e60c/scala/scala-impl/src/org/jetbrains/sbt/language/SbtFileImpl.scala#L82-L84
  private def toSbtTargetIdName(ref: LoadedBuildUnit): String = {
    ref.root + "-build"
  }
  private def toSbtTargetId(ref: LoadedBuildUnit): BuildTargetIdentifier = {
    val name = toSbtTargetIdName(ref)
    val build = ref.unit.uri
    val sanitized = build.toString.indexOf("#") match {
      case i if i > 0 => build.toString.take(i)
      case _          => build.toString
    }
    BuildTargetIdentifier(new URI(sanitized + "#" + name))
  }
  private def toId(ref: ProjectReference, config: Configuration): BuildTargetIdentifier =
    ref match {
      case ProjectRef(build, project) =>
        val sanitized = build.toString.indexOf("#") match {
          case i if i > 0 => build.toString.take(i)
          case _          => build.toString
        }
        BuildTargetIdentifier(new URI(s"$sanitized#$project/${config.id}"))
      case _ => sys.error(s"unexpected $ref")
    }

  private def anyOrThrow[T](results: Seq[Result[T]]): Seq[T] = {
    val successes = results.collect { case Value(v) => v }
    val errors = results.collect { case Inc(cause)  => cause }
    if (successes.nonEmpty || errors.isEmpty) successes
    else throw Incomplete(None, causes = errors)
  }

  private def allOrThrow[T](results: Seq[Result[T]]): Seq[T] = {
    val successes = results.collect { case Value(v) => v }
    val errors = results.collect { case Inc(cause)  => cause }
    if (errors.isEmpty) successes
    else throw Incomplete(None, causes = errors)
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

  /** The regular targets for each scope and meta-targets for the SBT build. */
  private[sbt] final case class BspFullWorkspace(
      scopes: Map[BuildTargetIdentifier, Scope],
      builds: Map[BuildTargetIdentifier, LoadedBuildUnit],
      buildToScope: Map[BuildTargetIdentifier, Seq[BuildTargetIdentifier]]
  ) {
    def filter(targets: Seq[BuildTargetIdentifier]): BspFullWorkspace = {
      val set = targets.toSet
      def filterMap[T](map: Map[BuildTargetIdentifier, T]) = map.filter(x => set.contains(x._1))
      BspFullWorkspace(filterMap(scopes), filterMap(builds), buildToScope)
    }
    def warnIfBuildsNonEmpty(method: String, log: Logger): Unit = {
      if (builds.nonEmpty)
        log.warn(
          s"$method is a no-op for build.sbt targets: ${builds.keys.mkString("[", ",", "]")}"
        )
    }
  }

  /**
   * Additional information about compilation status for given build target.
   *
   * @param hasAnyProblems keeps track of problems in given file so BSP reporter
   * can omit unnecessary diagnostics updates.
   * @param compiledAtLeastOnce keeps track of those projects that were compiled at
   * least once so that we can decide to enable fresh reporting for projects that
   * are compiled for the first time.
   * see: https://github.com/scalacenter/bloop/issues/726
   */
  private[server] final class BspCompileState {
    val hasAnyProblems: java.util.Set[Path] =
      java.util.concurrent.ConcurrentHashMap.newKeySet[Path]
    val compiledAtLeastOnce: AtomicBoolean = new AtomicBoolean(false)
  }
}
