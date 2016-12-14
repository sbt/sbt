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

import sbt._
import Keys._

import CrossVersion._

import sbt.complete.Parser

import org.apache.ivy.core.resolve.ResolveOptions

import net.virtualvoid.sbt.graph.backend.{ IvyReport, SbtUpdateReport }
import net.virtualvoid.sbt.graph.rendering.DagreHTML
import net.virtualvoid.sbt.graph.util.IOUtil

object DependencyGraphSettings {
  import DependencyGraphKeys._
  import ModuleGraphProtocol._

  def graphSettings = Seq(
    ivyReportFunction <<= ivyReportFunctionTask,
    updateConfiguration in ignoreMissingUpdate <<= updateConfiguration(config ⇒ new UpdateConfiguration(config.retrieve, true, config.logging)),
    ignoreMissingUpdateT,
    filterScalaLibrary in Global := true) ++ Seq(Compile, Test, IntegrationTest, Runtime, Provided, Optional).flatMap(ivyReportForConfig)

  def ivyReportForConfig(config: Configuration) = inConfig(config)(Seq(
    ivyReport <<= ivyReportFunction map (_(config.toString)) dependsOn (ignoreMissingUpdate),
    crossProjectId <<= (scalaVersion, scalaBinaryVersion, projectID)((sV, sBV, id) ⇒ CrossVersion(sV, sBV)(id)),
    moduleGraphSbt <<= moduleGraphSbtTask,
    moduleGraphIvyReport <<= moduleGraphIvyReportTask,
    moduleGraph <<= (sbtVersion, moduleGraphSbt, moduleGraphIvyReport) { (version, graphSbt, graphIvy) ⇒
      version match {
        case Version(0, 13, x, _) if x >= 6 ⇒ graphSbt
        case _                              ⇒ graphIvy
      }
    },
    moduleGraph <<= (scalaVersion, moduleGraph, filterScalaLibrary) map { (scalaV, graph, filter) ⇒
      if (filter) GraphTransformations.ignoreScalaLibrary(scalaV, graph)
      else graph
    },
    moduleGraphStore <<= moduleGraph storeAs moduleGraphStore triggeredBy moduleGraph,
    asciiGraph <<= moduleGraph map rendering.AsciiGraph.asciiGraph,
    dependencyGraph <<= InputTask(shouldForceParser) { force ⇒
      (force, moduleGraph, streams) map { (force, graph, streams) ⇒
        if (force || graph.nodes.size < 15) {
          streams.log.info(rendering.AsciiGraph.asciiGraph(graph))
          streams.log.info("\n\n")
          streams.log.info("Note: The old tree layout is still available by using `dependency-tree`")
        } else {
          streams.log.info(rendering.AsciiTree.asciiTree(graph))

          if (!force) {
            streams.log.info("\n")
            streams.log.info("Note: The graph was estimated to be too big to display (> 15 nodes). Use `sbt 'dependency-graph --force'` (with the single quotes) to force graph display.")
          }
        }
      }
    },
    asciiTree <<= moduleGraph map rendering.AsciiTree.asciiTree,
    dependencyTree <<= print(asciiTree),
    dependencyGraphMLFile <<= target / "dependencies-%s.graphml".format(config.toString),
    dependencyGraphML <<= dependencyGraphMLTask,
    dependencyDotFile <<= target / "dependencies-%s.dot".format(config.toString),
    dependencyDotString <<= dependencyDotStringTask,
    dependencyDot <<= writeToFile(dependencyDotString, dependencyDotFile),
    dependencyBrowseGraphTarget <<= target / "browse-dependency-graph",
    dependencyBrowseGraphHTML <<= browseGraphHTMLTask,
    dependencyBrowseGraph <<= (dependencyBrowseGraphHTML, streams).map { (uri, streams) ⇒
      streams.log.info("Opening in browser...")
      java.awt.Desktop.getDesktop.browse(uri)
      uri
    },
    dependencyList <<= printFromGraph(rendering.FlatList.render(_, _.id.idString)),
    dependencyStats <<= printFromGraph(rendering.Statistics.renderModuleStatsList),
    dependencyDotHeader := """digraph "dependency-graph" {
                             |    graph[rankdir="LR"]
                             |    edge [
                             |        arrowtail="none"
                             |    ]""".stripMargin,
    dependencyDotNodeLabel := { (organisation: String, name: String, version: String) ⇒
      """%s<BR/><B>%s</B><BR/>%s""".format(organisation, name, version)
    },
    whatDependsOn <<= InputTask(artifactIdParser) { module ⇒
      (module, streams, moduleGraph) map { (module, streams, graph) ⇒
        if (module.version.isEmpty) {
          val modules = graph.reverseDependencyMap.filter(m ⇒ m._1.copy(version = "") == module).keys
          modules.foreach(m ⇒ streams.log.info(rendering.AsciiTree.asciiTree(GraphTransformations.reverseGraphStartingAt(graph, m))))
        } else streams.log.info(rendering.AsciiTree.asciiTree(GraphTransformations.reverseGraphStartingAt(graph, module)))
      }
    },
    licenseInfo <<= (moduleGraph, streams) map showLicenseInfo))

  def ivyReportFunctionTask =
    (sbtVersion, target, projectID, ivyModule, appConfiguration, streams) map { (sbtV, target, projectID, ivyModule, config, streams) ⇒
      sbtV match {
        case Version(0, min, fix, _) if min > 12 || (min == 12 && fix >= 3) ⇒
          (c: String) ⇒ file("%s/resolution-cache/reports/%s-%s-%s.xml".format(target, projectID.organization, crossName(ivyModule), c))
          case Version(0, min, fix, _) if min == 12 && fix >= 1 && fix < 3 ⇒
          ivyModule.withModule(streams.log) { (i, moduleDesc, _) ⇒
            val id = ResolveOptions.getDefaultResolveId(moduleDesc)
            (c: String) ⇒ file("%s/resolution-cache/reports/%s/%s-resolved.xml" format (target, id, c))
          }
        case _ ⇒
          val home = config.provider.scalaProvider.launcher.ivyHome
          (c: String) ⇒ file("%s/cache/%s-%s-%s.xml" format (home, projectID.organization, crossName(ivyModule), c))
      }
    }

  def moduleGraphIvyReportTask = ivyReport map (absoluteReportPath.andThen(IvyReport.fromReportFile))
  def moduleGraphSbtTask =
    (ignoreMissingUpdate, crossProjectId, configuration) map { (update, root, config) ⇒
      update.configuration(config.name).map(report ⇒ SbtUpdateReport.fromConfigurationReport(report, root)).getOrElse(ModuleGraph.empty)
    }

  def printAsciiGraphTask =
    (streams, asciiGraph) map (_.log.info(_))

  def dependencyGraphMLTask =
    (moduleGraph, dependencyGraphMLFile, streams) map { (graph, resultFile, streams) ⇒
      rendering.GraphML.saveAsGraphML(graph, resultFile.getAbsolutePath)
      streams.log.info("Wrote dependency graph to '%s'" format resultFile)
      resultFile
    }
  def dependencyDotStringTask =
    (moduleGraph, dependencyDotHeader, dependencyDotNodeLabel).map {
      (graph, dotHead, nodeLabel) ⇒ rendering.DOT.dotGraph(graph, dotHead, nodeLabel, rendering.DOT.AngleBrackets)
    }

  def browseGraphHTMLTask =
    (moduleGraph, dependencyDotHeader, dependencyDotNodeLabel, dependencyBrowseGraphTarget, streams).map { (graph, dotHead, nodeLabel, target, streams) ⇒
      val dotGraph = rendering.DOT.dotGraph(graph, dotHead, nodeLabel, rendering.DOT.LabelTypeHtml)
      val link = DagreHTML.createLink(dotGraph, target)
      streams.log.info(s"HTML graph written to $link")
      link
    }

  def writeToFile(dataTask: TaskKey[String], fileTask: SettingKey[File]) =
    (dataTask, fileTask, streams).map { (data, outFile, streams) ⇒
      IOUtil.writeToFile(data, outFile)

      streams.log.info("Wrote dependency graph to '%s'" format outFile)
      outFile
    }

  def absoluteReportPath = (file: File) ⇒ file.getAbsolutePath

  def print(key: TaskKey[String]) =
    (streams, key) map (_.log.info(_))

  def printFromGraph(f: ModuleGraph ⇒ String) =
    (streams, moduleGraph) map ((streams, graph) ⇒ streams.log.info(f(graph)))

  def showLicenseInfo(graph: ModuleGraph, streams: TaskStreams) {
    val output =
      graph.nodes.filter(_.isUsed).groupBy(_.license).toSeq.sortBy(_._1).map {
        case (license, modules) ⇒
          license.getOrElse("No license specified") + "\n" +
            modules.map(_.id.idString formatted "\t %s").mkString("\n")
      }.mkString("\n\n")
    streams.log.info(output)
  }

  import Project._
  val shouldForceParser: State ⇒ Parser[Boolean] = { (state: State) ⇒
    import sbt.complete.DefaultParsers._

    (Space ~> token("--force")).?.map(_.isDefined)
  }

  val artifactIdParser: Initialize[State ⇒ Parser[ModuleId]] =
    resolvedScoped { ctx ⇒
      (state: State) ⇒
        val graph = loadFromContext(moduleGraphStore, ctx, state) getOrElse ModuleGraph(Nil, Nil)

        import sbt.complete.DefaultParsers._
        graph.nodes.map(_.id).map {
          case id @ ModuleId(org, name, version) ⇒
            (Space ~ token(org) ~ token(Space ~ name) ~ token(Space ~ version).?).map(_._2 match {
              case Some(_) ⇒ id
              case None    ⇒ id.copy(version = "")
            })
        }.reduceOption(_ | _).getOrElse {
          (Space ~> token(StringBasic, "organization") ~ Space ~ token(StringBasic, "module") ~ (Space ~ token(StringBasic, "version")).?).map {
            case (((org, _), mod), version) ⇒
              ModuleId(org, mod, version.map(_._2).getOrElse(""))
          }
        }
    }

  // This is to support 0.13.8's InlineConfigurationWithExcludes while not forcing 0.13.8
  type HasModule = {
    val module: ModuleID
  }
  def crossName(ivyModule: IvySbt#Module) =
    ivyModule.moduleSettings match {
      case ic: InlineConfiguration ⇒ ic.module.name
      case hm: HasModule if hm.getClass.getName == "sbt.InlineConfigurationWithExcludes" ⇒ hm.module.name
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

  /**
   * This is copied directly from sbt/main/Defaults.java and then changed to update the UpdateConfiguration
   * to ignore missing artifacts.
   */
  def ignoreMissingUpdateT =
    ignoreMissingUpdate <<= Def.task {
      val depsUpdated = transitiveUpdate.value.exists(!_.stats.cached)
      val isRoot = executionRoots.value contains resolvedScoped.value
      val s = streams.value
      val scalaProvider = appConfiguration.value.provider.scalaProvider

      // Only substitute unmanaged jars for managed jars when the major.minor parts of the versions the same for:
      //   the resolved Scala version and the scalaHome version: compatible (weakly- no qualifier checked)
      //   the resolved Scala version and the declared scalaVersion: assume the user intended scalaHome to override anything with scalaVersion
      def subUnmanaged(subVersion: String, jars: Seq[File]) = (sv: String) ⇒
        (partialVersion(sv), partialVersion(subVersion), partialVersion(scalaVersion.value)) match {
          case (Some(res), Some(sh), _) if res == sh     ⇒ jars
          case (Some(res), _, Some(decl)) if res == decl ⇒ jars
          case _                                         ⇒ Nil
        }
      val subScalaJars: String ⇒ Seq[File] = SbtAccess.unmanagedScalaInstanceOnly.value match {
        case Some(si) ⇒ subUnmanaged(si.version, si.jars)
        case None     ⇒ sv ⇒ if (scalaProvider.version == sv) scalaProvider.jars else Nil
      }
      val transform: UpdateReport ⇒ UpdateReport = r ⇒ Classpaths.substituteScalaFiles(scalaOrganization.value, r)(subScalaJars)

      val show = Reference.display(thisProjectRef.value)
      Classpaths.cachedUpdate(s.cacheDirectory, show, ivyModule.value, (updateConfiguration in ignoreMissingUpdate).value, transform, skip = (skip in update).value, force = isRoot, depsUpdated = depsUpdated, log = s.log)
    }
}
