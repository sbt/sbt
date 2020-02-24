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
import sbt.librarymanagement.{ Configuration, Configurations }
import Configurations.{ Compile, Test }
import sbt.SlashSyntax0._
import sbt.BuildSyntax._
import scala.collection.mutable
import sjsonnew.support.scalajson.unsafe.Converter

object BuildServerProtocol {
  private[sbt] val idMap: mutable.Map[BuildTargetIdentifier, (ProjectRef, Configuration)] =
    mutable.Map.empty
  val BspBuildTargetSource = "bspBuildTargetSources"

  def commands: List[Command] = List(bspBuildTargetSources)

  /**
   * Command that expects list of URIs.
   * https://github.com/build-server-protocol/build-server-protocol/blob/master/docs/specification.md#build-target-sources-request
   */
  def bspBuildTargetSources: Command = Command.args(BspBuildTargetSource, "<args>") {
    (s0: State, args: Seq[String]) =>
      import sbt.internal.bsp.codec.JsonProtocol._
      var s: State = s0
      val items = args map { arg =>
        val id = BuildTargetIdentifier(new URI(arg))
        val pair = idMap(id)
        println(pair.toString)
        val dirs = s0.setting(pair._1 / pair._2 / Keys.unmanagedSourceDirectories)
        val (next, managed) = s.unsafeRunTask(pair._1 / pair._2 / Keys.managedSources)
        s = next
        val items = (dirs.toVector map { dir =>
          SourceItem(dir.toURI, SourceItemKind.Directory, false)
        }) ++
          (managed.toVector map { x =>
            SourceItem(x.toURI, SourceItemKind.File, true)
          })
        SourcesItem(id, items)
      }
      val result = SourcesResult(items.toVector)
      s0.respondEvent(result)
      s
  }

  // def json(s: String): JValue = Parser.parseUnsafe(s)

  def toId(ref: ProjectReference, config: Configuration): BuildTargetIdentifier =
    ref match {
      case ProjectRef(build, project) =>
        BuildTargetIdentifier(new URI(s"$build#$project/${config.id}"))
      case _ => sys.error(s"unexpected $ref")
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
      case (fr, "compile") if fr == from.name => Some(toId(ref.project, Compile))
      case (fr, "test") if fr == from.name    => Some(toId(ref.project, Test))
      case _                                  => None
    }
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
        val compileId = toId(ref, Compile)
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
        val testId = toId(ref, Test)
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
}
