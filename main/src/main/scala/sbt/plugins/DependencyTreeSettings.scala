/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import java.io.File

import sbt.Def._
import sbt.Keys._
import sbt.SlashSyntax0._
import sbt.Project._
import sbt.internal.graph._
import sbt.internal.graph.backend.SbtUpdateReport
import sbt.internal.graph.rendering.{ DagreHTML, TreeView }
import sbt.internal.librarymanagement._
import sbt.internal.util.complete.{ Parser, Parsers }
import sbt.io.IO
import sbt.io.syntax._
import sbt.librarymanagement._

object DependencyTreeSettings {
  import sjsonnew.BasicJsonProtocol._
  import MiniDependencyTreeKeys._
  import DependencyTreeKeys._

  /**
   * Core settings needed for any graphing tasks.
   */
  def coreSettings =
    Seq(
      // disable the cached resolution engine (exposing a scoped `ivyModule` used directly by `updateTask`), as it
      // generates artificial module descriptors which are internal to sbt, making it hard to reconstruct the
      // dependency tree
      dependencyTreeIgnoreMissingUpdate / updateOptions := updateOptions.value
        .withCachedResolution(false),
      dependencyTreeIgnoreMissingUpdate / ivyConfiguration := {
        // inTask will make sure the new definition will pick up `updateOptions in dependencyTreeIgnoreMissingUpdate`
        inTask(dependencyTreeIgnoreMissingUpdate, Classpaths.mkIvyConfiguration).value
      },
      dependencyTreeIgnoreMissingUpdate / ivyModule := {
        // concatenating & inlining ivySbt & ivyModule default task implementations, as `SbtAccess.inTask` does
        // NOT correctly force the scope when applied to `TaskKey.toTask` instances (as opposed to raw
        // implementations like `Classpaths.mkIvyConfiguration` or `Classpaths.updateTask`)
        val is = new IvySbt((dependencyTreeIgnoreMissingUpdate / ivyConfiguration).value)
        new is.Module(moduleSettings.value)
      },
      // don't fail on missing dependencies
      dependencyTreeIgnoreMissingUpdate / updateConfiguration := updateConfiguration.value
        .withMissingOk(true),
      dependencyTreeIgnoreMissingUpdate := {
        // inTask will make sure the new definition will pick up `ivyModule/updateConfiguration in ignoreMissingUpdate`
        inTask(dependencyTreeIgnoreMissingUpdate, Classpaths.updateTask).value
      },
    )

  /**
   * MiniDependencyTreePlugin includes these settings for Compile and Test scopes
   * to provide dependencyTree task.
   */
  lazy val baseBasicReportingSettings: Seq[Def.Setting[_]] =
    Seq(
      dependencyTreeCrossProjectId := CrossVersion(scalaVersion.value, scalaBinaryVersion.value)(
        projectID.value
      ),
      dependencyTreeModuleGraph0 := {
        val sv = scalaVersion.value
        val g = dependencyTreeIgnoreMissingUpdate.value
          .configuration(configuration.value)
          .map(
            report =>
              SbtUpdateReport.fromConfigurationReport(report, dependencyTreeCrossProjectId.value)
          )
          .getOrElse(ModuleGraph.empty)
        if (dependencyTreeIncludeScalaLibrary.value) g
        else GraphTransformations.ignoreScalaLibrary(sv, g)
      },
      dependencyTreeModuleGraphStore := (dependencyTreeModuleGraph0 storeAs dependencyTreeModuleGraphStore triggeredBy dependencyTreeModuleGraph0).value,
    ) ++ {
      renderingTaskSettings(dependencyTree) :+ {
        dependencyTree / asString := {
          rendering.AsciiTree.asciiTree(dependencyTreeModuleGraph0.value, asciiGraphWidth.value)
        }
      }
    }

  /**
   * This is the maximum strength settings for DependencyTreePlugin.
   */
  lazy val baseFullReportingSettings: Seq[Def.Setting[_]] =
    Seq(
      // browse
      dependencyBrowseGraphTarget := { target.value / "browse-dependency-graph" },
      dependencyBrowseGraphHTML := browseGraphHTMLTask.value,
      dependencyBrowseGraph := openBrowser(dependencyBrowseGraphHTML).value,
      dependencyBrowseTreeTarget := { target.value / "browse-dependency-tree" },
      dependencyBrowseTreeHTML := browseTreeHTMLTask.value,
      dependencyBrowseTree := openBrowser(dependencyBrowseTreeHTML).value,
      // dot support
      dependencyDotFile := {
        val config = configuration.value
        target.value / "dependencies-%s.dot".format(config.toString)
      },
      dependencyDot / asString := rendering.DOT.dotGraph(
        dependencyTreeModuleGraph0.value,
        dependencyDotHeader.value,
        dependencyDotNodeLabel.value,
        rendering.DOT.AngleBrackets
      ),
      dependencyDot := writeToFile(dependencyDot / asString, dependencyDotFile).value,
      dependencyDotHeader :=
        """|digraph "dependency-graph" {
         |    graph[rankdir="LR"]
         |    edge [
         |        arrowtail="none"
         |    ]""".stripMargin,
      dependencyDotNodeLabel := { (organization: String, name: String, version: String) =>
        """%s<BR/><B>%s</B><BR/>%s""".format(organization, name, version)
      },
      // GraphML support
      dependencyGraphMLFile := {
        val config = configuration.value
        target.value / "dependencies-%s.graphml".format(config.toString)
      },
      dependencyGraphML := dependencyGraphMLTask.value,
      whatDependsOn := {
        val ArtifactPattern(org, name, versionFilter) = artifactPatternParser.parsed
        val graph = dependencyTreeModuleGraph0.value
        val modules =
          versionFilter match {
            case Some(version) => GraphModuleId(org, name, version) :: Nil
            case None =>
              graph.nodes.filter(m => m.id.organization == org && m.id.name == name).map(_.id)
          }
        val graphWidth = asciiGraphWidth.value
        val output =
          modules
            .map { module =>
              rendering.AsciiTree
                .asciiTree(GraphTransformations.reverseGraphStartingAt(graph, module), graphWidth)
            }
            .mkString("\n")

        streams.value.log.info(output)
        output
      },
    ) ++
      renderingAlternatives.flatMap { case (key, renderer) => renderingTaskSettings(key, renderer) }

  def renderingAlternatives: Seq[(TaskKey[Unit], ModuleGraph => String)] =
    Seq(
      dependencyList -> rendering.FlatList.render(_.id.idString),
      dependencyStats -> rendering.Statistics.renderModuleStatsList _,
      dependencyLicenseInfo -> rendering.LicenseInfo.render _
    )

  def renderingTaskSettings(key: TaskKey[Unit], renderer: ModuleGraph => String): Seq[Setting[_]] =
    renderingTaskSettings(key) :+ {
      key / asString := renderer(dependencyTreeModuleGraph0.value)
    }

  def renderingTaskSettings(key: TaskKey[Unit]): Seq[Setting[_]] =
    Seq(
      key := {
        val s = streams.value
        val str = (key / asString).value
        s.log.info(str)
      },
      key / toFile := {
        val (targetFile, force) = targetFileAndForceParser.parsed
        writeToFile(key.key.label, (key / asString).value, targetFile, force, streams.value)
      },
    )

  def dependencyGraphMLTask =
    Def.task {
      val resultFile = dependencyGraphMLFile.value
      val graph = dependencyTreeModuleGraph0.value
      rendering.GraphML.saveAsGraphML(graph, resultFile.getAbsolutePath)
      streams.value.log.info("Wrote dependency graph to '%s'" format resultFile)
      resultFile
    }

  def browseGraphHTMLTask =
    Def.task {
      val graph = dependencyTreeModuleGraph0.value
      val dotGraph = rendering.DOT.dotGraph(
        graph,
        dependencyDotHeader.value,
        dependencyDotNodeLabel.value,
        rendering.DOT.LabelTypeHtml
      )
      val link = DagreHTML.createLink(dotGraph, target.value)
      streams.value.log.info(s"HTML graph written to $link")
      link
    }

  def browseTreeHTMLTask =
    Def.task {
      val graph = dependencyTreeModuleGraph0.value
      val renderedTree = TreeView.createJson(graph)
      val link = TreeView.createLink(renderedTree, target.value)
      streams.value.log.info(s"HTML tree written to $link")
      link
    }

  def writeToFile(dataTask: TaskKey[String], fileTask: SettingKey[File]) =
    Def.task {
      val outFile = fileTask.value
      IO.write(outFile, dataTask.value, IO.utf8)

      streams.value.log.info("Wrote dependency graph to '%s'" format outFile)
      outFile
    }

  def writeToFile(
      what: String,
      data: String,
      targetFile: File,
      force: Boolean,
      streams: TaskStreams
  ): File =
    if (targetFile.exists && !force)
      throw new RuntimeException(
        s"Target file for $what already exists at ${targetFile.getAbsolutePath}. Use '-f' to override"
      )
    else {
      IO.write(targetFile, data, IO.utf8)

      streams.log.info(s"Wrote $what to '$targetFile'")
      targetFile
    }

  def absoluteReportPath = (file: File) => file.getAbsolutePath

  def openBrowser(uriKey: TaskKey[URI]) =
    Def.task {
      val uri = uriKey.value
      streams.value.log.info("Opening in browser...")
      java.awt.Desktop.getDesktop.browse(uri)
      uri
    }

  case class ArtifactPattern(organization: String, name: String, version: Option[String])

  import sbt.internal.util.complete.DefaultParsers._
  val artifactPatternParser: Def.Initialize[State => Parser[ArtifactPattern]] =
    Keys.resolvedScoped { ctx => (state: State) =>
      val graph = Defaults.loadFromContext(dependencyTreeModuleGraphStore, ctx, state) getOrElse ModuleGraph(
        Nil,
        Nil
      )

      graph.nodes
        .map(_.id)
        .groupBy(m => (m.organization, m.name))
        .map {
          case ((org, name), modules) =>
            val versionParsers: Seq[Parser[Option[String]]] =
              modules.map { id =>
                token(Space ~> id.version).?
              }

            (Space ~> token(org) ~ token(Space ~> name) ~ oneOf(versionParsers)).map {
              case ((org, name), version) => ArtifactPattern(org, name, version)
            }
        }
        .reduceOption(_ | _)
        .getOrElse {
          // If the dependencyTreeModuleGraphStore couldn't be loaded because no dependency tree command was run before, we should still provide a parser for the command.
          ((Space ~> token(StringBasic, "<organization>")) ~ (Space ~> token(
            StringBasic,
            "<module>"
          )) ~ (Space ~> token(StringBasic, "<version?>")).?).map {
            case ((org, mod), version) =>
              ArtifactPattern(org, mod, version)
          }
        }
    }
  val shouldForceParser: Parser[Boolean] =
    (Space ~> (Parser.literal("-f") | "--force")).?.map(_.isDefined)

  val targetFileAndForceParser: Parser[(File, Boolean)] =
    Parsers.fileParser(new File(".")) ~ shouldForceParser

  // This is to support 0.13.8's InlineConfigurationWithExcludes while not forcing 0.13.8
  type HasModule = {
    val module: ModuleID
  }
  def crossName(ivyModule: IvySbt#Module) =
    ivyModule.moduleSettings match {
      case ic: ModuleDescriptorConfiguration => ic.module.name
      case _ =>
        throw new IllegalStateException(
          "sbt-dependency-graph plugin currently only supports ModuleDescriptorConfiguration of ivy settings (the default in sbt)"
        )
    }

  val VersionPattern = """(\d+)\.(\d+)\.(\d+)(?:-(.*))?""".r
  object Version {
    def unapply(str: String): Option[(Int, Int, Int, Option[String])] = str match {
      case VersionPattern(major, minor, fix, appendix) =>
        Some((major.toInt, minor.toInt, fix.toInt, Option(appendix)))
      case _ => None
    }
  }
}
