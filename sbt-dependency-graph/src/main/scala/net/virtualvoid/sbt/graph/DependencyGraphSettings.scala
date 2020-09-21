/*
 * Copyright 2015 Johannes Rudolph
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.virtualvoid.sbt.graph

import scala.language.reflectiveCalls
import sbt._
import Keys._
import sbt.complete.Parser
import net.virtualvoid.sbt.graph.backend.{ IvyReport, SbtUpdateReport }
import net.virtualvoid.sbt.graph.rendering.{ AsciiGraph, DagreHTML, TreeView }
import net.virtualvoid.sbt.graph.util.IOUtil
import internal.librarymanagement._
import librarymanagement._
import sbt.dependencygraph.SbtAccess
import sbt.dependencygraph.DependencyGraphSbtCompat.Implicits._
import sbt.complete.Parsers

object DependencyGraphSettings {
  import DependencyGraphKeys._
  import ModuleGraphProtocol._

  def graphSettings = baseSettings ++ reportSettings

  def baseSettings = Seq(
    ivyReportFunction := ivyReportFunctionTask.value,

    // disable the cached resolution engine (exposing a scoped `ivyModule` used directly by `updateTask`), as it
    // generates artificial module descriptors which are internal to sbt, making it hard to reconstruct the
    // dependency tree
    updateOptions in ignoreMissingUpdate := updateOptions.value.withCachedResolution(false),
    ivyConfiguration in ignoreMissingUpdate :=
      // inTask will make sure the new definition will pick up `updateOptions in ignoreMissingUpdate`
      SbtAccess.inTask(ignoreMissingUpdate, Classpaths.mkIvyConfiguration).value,
    ivyModule in ignoreMissingUpdate := {
      // concatenating & inlining ivySbt & ivyModule default task implementations, as `SbtAccess.inTask` does
      // NOT correctly force the scope when applied to `TaskKey.toTask` instances (as opposed to raw
      // implementations like `Classpaths.mkIvyConfiguration` or `Classpaths.updateTask`)
      val is = new IvySbt((ivyConfiguration in ignoreMissingUpdate).value)
      new is.Module(moduleSettings.value)
    },

    // don't fail on missing dependencies
    updateConfiguration in ignoreMissingUpdate := updateConfiguration.value.withMissingOk(true),

    ignoreMissingUpdate :=
      // inTask will make sure the new definition will pick up `ivyModule/updateConfiguration in ignoreMissingUpdate`
      SbtAccess.inTask(ignoreMissingUpdate, Classpaths.updateTask).value,

    filterScalaLibrary in Global := true)

  def reportSettings =
    Seq(Compile, Test, IntegrationTest, Runtime, Provided, Optional).flatMap(ivyReportForConfig)

  val renderingAlternatives: Seq[(TaskKey[Unit], ModuleGraph ⇒ String)] =
    Seq(
      dependencyTree -> rendering.AsciiTree.asciiTree _,
      dependencyList -> rendering.FlatList.render(_.id.idString),
      dependencyStats -> rendering.Statistics.renderModuleStatsList _,
      licenseInfo -> rendering.LicenseInfo.render _)

  def ivyReportForConfig(config: Configuration) = inConfig(config)(
    Seq(
      ivyReport := { Def.task { ivyReportFunction.value.apply(config.toString) } dependsOn (ignoreMissingUpdate) }.value,
      crossProjectId := sbt.CrossVersion(scalaVersion.value, scalaBinaryVersion.value)(projectID.value),
      moduleGraphSbt :=
        ignoreMissingUpdate.value.configuration(configuration.value).map(report ⇒ SbtUpdateReport.fromConfigurationReport(report, crossProjectId.value)).getOrElse(ModuleGraph.empty),
      moduleGraphIvyReport := IvyReport.fromReportFile(absoluteReportPath(ivyReport.value)),
      moduleGraph := {
        sbtVersion.value match {
          case Version(0, 13, x, _) if x >= 6 ⇒ moduleGraphSbt.value
          case Version(1, _, _, _)            ⇒ moduleGraphSbt.value
        }
      },
      moduleGraph := {
        // FIXME: remove busywork
        val scalaVersion = Keys.scalaVersion.value
        val moduleGraph = DependencyGraphKeys.moduleGraph.value

        if (filterScalaLibrary.value) GraphTransformations.ignoreScalaLibrary(scalaVersion, moduleGraph)
        else moduleGraph
      },
      moduleGraphStore := (moduleGraph storeAs moduleGraphStore triggeredBy moduleGraph).value,

      // browse
      dependencyBrowseGraphTarget := { target.value / "browse-dependency-graph" },
      dependencyBrowseGraphHTML := browseGraphHTMLTask.value,
      dependencyBrowseGraph := openBrowser(dependencyBrowseGraphHTML).value,

      dependencyBrowseTreeTarget := { target.value / "browse-dependency-tree" },
      dependencyBrowseTreeHTML := browseTreeHTMLTask.value,
      dependencyBrowseTree := openBrowser(dependencyBrowseTreeHTML).value,

      // dot support
      dependencyDotFile := { target.value / "dependencies-%s.dot".format(config.toString) },
      dependencyDotString := rendering.DOT.dotGraph(moduleGraph.value, dependencyDotHeader.value, dependencyDotNodeLabel.value, rendering.DOT.AngleBrackets),
      dependencyDot := writeToFile(dependencyDotString, dependencyDotFile).value,
      dependencyDotHeader :=
        """|digraph "dependency-graph" {
         |    graph[rankdir="LR"]
         |    edge [
         |        arrowtail="none"
         |    ]""".stripMargin,
      dependencyDotNodeLabel := { (organisation: String, name: String, version: String) ⇒
        """%s<BR/><B>%s</B><BR/>%s""".format(organisation, name, version)
      },

      // GraphML support
      dependencyGraphMLFile := { target.value / "dependencies-%s.graphml".format(config.toString) },
      dependencyGraphML := dependencyGraphMLTask.value,

      whatDependsOn := {
        val ArtifactPattern(org, name, versionFilter) = artifactPatternParser.parsed
        val graph = moduleGraph.value
        val modules =
          versionFilter match {
            case Some(version) ⇒ ModuleId(org, name, version) :: Nil
            case None          ⇒ graph.nodes.filter(m ⇒ m.id.organisation == org && m.id.name == name).map(_.id)
          }
        val output =
          modules
            .map { module ⇒
              rendering.AsciiTree.asciiTree(GraphTransformations.reverseGraphStartingAt(graph, module))
            }
            .mkString("\n")

        streams.value.log.info(output)
        output
      },
      // deprecated settings
      asciiTree := (asString in dependencyTree).value) ++
      renderingAlternatives.flatMap((renderingTaskSettings _).tupled) ++
      AsciiGraph.asciiGraphSetttings)

  def renderingTaskSettings(key: TaskKey[Unit], renderer: ModuleGraph ⇒ String): Seq[Setting[_]] =
    Seq(
      asString in key := renderer(moduleGraph.value),
      printToConsole in key := streams.value.log.info((asString in key).value),
      toFile in key := {
        val (targetFile, force) = targetFileAndForceParser.parsed
        writeToFile(key.key.label, (asString in key).value, targetFile, force, streams.value)
      },
      key := (printToConsole in key).value)

  def ivyReportFunctionTask = Def.task {
    val ivyConfig = Keys.ivyConfiguration.value.asInstanceOf[InlineIvyConfiguration]
    val projectID = Keys.projectID.value
    val ivyModule = Keys.ivyModule.value

    (config: String) ⇒ {
      val org = projectID.organization
      val name = crossName(ivyModule)
      new File(ivyConfig.resolutionCacheDir.get, s"reports/$org-$name-$config.xml")
    }
  }

  def dependencyGraphMLTask =
    Def.task {
      val resultFile = dependencyGraphMLFile.value
      rendering.GraphML.saveAsGraphML(moduleGraph.value, resultFile.getAbsolutePath)
      streams.value.log.info("Wrote dependency graph to '%s'" format resultFile)
      resultFile
    }

  def browseGraphHTMLTask =
    Def.task {
      val dotGraph = rendering.DOT.dotGraph(moduleGraph.value, dependencyDotHeader.value, dependencyDotNodeLabel.value, rendering.DOT.LabelTypeHtml)
      val link = DagreHTML.createLink(dotGraph, target.value)
      streams.value.log.info(s"HTML graph written to $link")
      link
    }

  def browseTreeHTMLTask =
    Def.task {
      val renderedTree = TreeView.createJson(moduleGraph.value)
      val link = TreeView.createLink(renderedTree, target.value)
      streams.value.log.info(s"HTML tree written to $link")
      link
    }

  def writeToFile(dataTask: TaskKey[String], fileTask: SettingKey[File]) =
    Def.task {
      val outFile = fileTask.value
      IOUtil.writeToFile(dataTask.value, outFile)

      streams.value.log.info("Wrote dependency graph to '%s'" format outFile)
      outFile
    }

  def writeToFile(what: String, data: String, targetFile: File, force: Boolean, streams: TaskStreams): File =
    if (targetFile.exists && !force)
      throw new RuntimeException(s"Target file for $what already exists at ${targetFile.getAbsolutePath}. Use '-f' to override")
    else {
      IOUtil.writeToFile(data, targetFile)

      streams.log.info(s"Wrote $what to '$targetFile'")
      targetFile
    }

  def absoluteReportPath = (file: File) ⇒ file.getAbsolutePath

  def openBrowser(uriKey: TaskKey[URI]) =
    Def.task {
      val uri = uriKey.value
      streams.value.log.info("Opening in browser...")
      java.awt.Desktop.getDesktop.browse(uri)
      uri
    }

  case class ArtifactPattern(
    organisation: String,
    name:         String,
    version:      Option[String])

  import sbt.complete.DefaultParsers._
  val artifactPatternParser: Def.Initialize[State ⇒ Parser[ArtifactPattern]] =
    resolvedScoped { ctx ⇒ (state: State) ⇒
      val graph = loadFromContext(moduleGraphStore, ctx, state) getOrElse ModuleGraph(Nil, Nil)

      graph.nodes
        .map(_.id)
        .groupBy(m ⇒ (m.organisation, m.name))
        .map {
          case ((org, name), modules) ⇒
            val versionParsers: Seq[Parser[Option[String]]] =
              modules.map { id ⇒
                token(Space ~> id.version).?
              }

            (Space ~> token(org) ~ token(Space ~> name) ~ oneOf(versionParsers)).map {
              case ((org, name), version) ⇒ ArtifactPattern(org, name, version)
            }
        }
        .reduceOption(_ | _).getOrElse {
          // If the moduleGraphStore couldn't be loaded because no dependency tree command was run before, we should still provide a parser for the command.
          ((Space ~> token(StringBasic, "<organization>")) ~ (Space ~> token(StringBasic, "<module>")) ~ (Space ~> token(StringBasic, "<version?>")).?).map {
            case ((org, mod), version) ⇒
              ArtifactPattern(org, mod, version)
          }
        }
    }
  val shouldForceParser: Parser[Boolean] = (Space ~> (Parser.literal("-f") | "--force")).?.map(_.isDefined)

  val targetFileAndForceParser: Parser[(File, Boolean)] =
    Parsers.fileParser(new File(".")) ~ shouldForceParser

  // This is to support 0.13.8's InlineConfigurationWithExcludes while not forcing 0.13.8
  type HasModule = {
    val module: ModuleID
  }
  def crossName(ivyModule: IvySbt#Module) =
    ivyModule.moduleSettings match {
      case ic: InlineConfiguration ⇒ ic.module.name
      case hm: HasModule @unchecked if hm.getClass.getName == "sbt.InlineConfigurationWithExcludes" ⇒ hm.module.name
      case _ ⇒
        throw new IllegalStateException("sbt-dependency-graph plugin currently only supports InlineConfiguration of ivy settings (the default in sbt)")
    }

  val VersionPattern = """(\d+)\.(\d+)\.(\d+)(?:-(.*))?""".r
  object Version {
    def unapply(str: String): Option[(Int, Int, Int, Option[String])] = str match {
      case VersionPattern(major, minor, fix, appendix) ⇒ Some((major.toInt, minor.toInt, fix.toInt, Option(appendix)))
      case _ ⇒ None
    }
  }
}
