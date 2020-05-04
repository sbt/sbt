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
import sbt.librarymanagement.Configurations.{ Compile, Test }
import sbt.std.TaskExtra._
import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import sjsonnew.support.scalajson.unsafe.Converter

object BuildServerProtocol {
  import sbt.internal.bsp.codec.JsonProtocol._
  private val bspVersion = "2.0.0-M5"
  private val capabilities = BuildClientCapabilities(languageIds = Vector("scala"))

  lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
    bspWorkspace := Def.taskDyn {
      val structure = Keys.buildStructure.value
      val scopes: Seq[Scope] = structure.allProjectRefs.flatMap { ref =>
        Seq(Scope.Global.in(ref, Compile), Scope.Global.in(ref, Test))
      }
      scopes
        .map(scope => (scope / Keys.buildTargetIdentifier).toTask)
        .joinWith(tasks => joinTasks(tasks).join.map(_.zip(scopes).toMap))
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
        SourceItem(dir.toURI, SourceItemKind.Directory, generated = false)
      }) ++
        (managed.toVector map { x =>
          SourceItem(x.toURI, SourceItemKind.File, generated = true)
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

  def handler(sbtVersion: String): ServerHandler = ServerHandler { callback =>
    ServerIntent(
      {
        case r: JsonRpcRequestMessage if r.method == "build/initialize" =>
          val _ = Converter.fromJson[InitializeBuildParams](json(r)).get
          val response = InitializeBuildResult("sbt", sbtVersion, bspVersion, capabilities, None)
          callback.jsonRpcRespond(response, Some(r.id)); ()

        case r: JsonRpcRequestMessage if r.method == "workspace/buildTargets" =>
          val _ = callback.appendExec(Keys.bspWorkspaceBuildTargets.key.toString, None)

        case r: JsonRpcRequestMessage if r.method == "build/shutdown" =>
          ()

        case r: JsonRpcRequestMessage if r.method == "build/exit" =>
          val _ = callback.appendExec("shutdown", None)

        case r: JsonRpcRequestMessage if r.method == "buildTarget/sources" =>
          import sbt.internal.bsp.codec.JsonProtocol._
          val param = Converter.fromJson[SourcesParams](json(r)).get
          val targets = param.targets.map(_.uri).mkString(" ")
          val command = Keys.bspBuildTargetSources.key
          val _ = callback.appendExec(s"$command $targets", None)

        case r if r.method == "buildTarget/compile" =>
          val param = Converter.fromJson[CompileParams](json(r)).get
          callback.log.info(param.toString)
          val targets = param.targets.map(_.uri).mkString(" ")
          val command = Keys.bspBuildTargetCompile.key
          val _ = callback.appendExec(s"$command $targets", None)

        case r: JsonRpcRequestMessage if r.method == "buildTarget/scalacOptions" =>
          import sbt.internal.bsp.codec.JsonProtocol._
          val param = Converter.fromJson[ScalacOptionsParams](json(r)).get
          val targets = param.targets.map(_.uri).mkString(" ")
          val command = Keys.bspBuildTargetScalacOptions.key
          val _ = callback.appendExec(s"$command $targets", None)
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
