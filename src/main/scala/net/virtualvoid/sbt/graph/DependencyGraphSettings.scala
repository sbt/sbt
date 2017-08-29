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
import net.virtualvoid.sbt.graph.rendering.{ AsciiGraph, DagreHTML }
import net.virtualvoid.sbt.graph.util.IOUtil

import sbt.compat.SbtCompat._
import internal.librarymanagement._
import librarymanagement._

object DependencyGraphSettings {
  import DependencyGraphKeys._
  import ModuleGraphProtocol._

  def graphSettings = Seq(
    ivyReportFunction := ivyReportFunctionTask.value,
    updateConfiguration in ignoreMissingUpdate := updateConfiguration.value.withMissingOk(true),
    ignoreMissingUpdate := update.value,
    filterScalaLibrary in Global := true) ++ Seq(Compile, Test, IntegrationTest, Runtime, Provided, Optional).flatMap(ivyReportForConfig)

  def ivyReportForConfig(config: Configuration) = inConfig(config)(Seq(
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
    asciiTree := rendering.AsciiTree.asciiTree(moduleGraph.value),
    dependencyTree := print(asciiTree).value,
    dependencyGraphMLFile := { target.value / "dependencies-%s.graphml".format(config.toString) },
    dependencyGraphML := dependencyGraphMLTask.value,
    dependencyDotFile := { target.value / "dependencies-%s.dot".format(config.toString) },
    dependencyDotString := rendering.DOT.dotGraph(moduleGraph.value, dependencyDotHeader.value, dependencyDotNodeLabel.value, rendering.DOT.AngleBrackets),
    dependencyDot := writeToFile(dependencyDotString, dependencyDotFile).value,
    dependencyBrowseGraphTarget := { target.value / "browse-dependency-graph" },
    dependencyBrowseGraphHTML := browseGraphHTMLTask.value,
    dependencyBrowseGraph := {
      val uri = dependencyBrowseGraphHTML.value
      streams.value.log.info("Opening in browser...")
      java.awt.Desktop.getDesktop.browse(uri)
      uri
    },
    dependencyList := printFromGraph(rendering.FlatList.render(_, _.id.idString)).value,
    dependencyStats := printFromGraph(rendering.Statistics.renderModuleStatsList).value,
    dependencyDotHeader := """digraph "dependency-graph" {
                             |    graph[rankdir="LR"]
                             |    edge [
                             |        arrowtail="none"
                             |    ]""".stripMargin,
    dependencyDotNodeLabel := { (organisation: String, name: String, version: String) ⇒
      """%s<BR/><B>%s</B><BR/>%s""".format(organisation, name, version)
    },
    whatDependsOn := {
      val module = artifactIdParser.parsed
      streams.value.log.info(rendering.AsciiTree.asciiTree(GraphTransformations.reverseGraphStartingAt(moduleGraph.value, module)))
    },
    licenseInfo := showLicenseInfo(moduleGraph.value, streams.value)) ++ AsciiGraph.asciiGraphSetttings)

  def ivyReportFunctionTask = Def.task {
    // FIXME: and remove busywork after https://github.com/sbt/sbt/issues/3299 is fixed
    val target = Keys.target.value
    val projectID = Keys.projectID.value
    val ivyModule = Keys.ivyModule.value

    sbtVersion.value match {
      case Version(0, min, fix, _) if min > 12 || (min == 12 && fix >= 3) ⇒
        (c: String) ⇒ file("%s/resolution-cache/reports/%s-%s-%s.xml".format(target, projectID.organization, crossName(ivyModule), c))
        case Version(0, min, fix, _) if min == 12 && fix >= 1 && fix < 3 ⇒
        ivyModule.withModule(streams.value.log) { (i, moduleDesc, _) ⇒
          val id = ResolveOptions.getDefaultResolveId(moduleDesc)
          (c: String) ⇒ file("%s/resolution-cache/reports/%s/%s-resolved.xml" format (target, id, c))
        }
      case _ ⇒
        val home = appConfiguration.value.provider.scalaProvider.launcher.ivyHome
        (c: String) ⇒ file("%s/cache/%s-%s-%s.xml" format (home, projectID.organization, crossName(ivyModule), c))
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

  def writeToFile(dataTask: TaskKey[String], fileTask: SettingKey[File]) =
    Def.task {
      val outFile = fileTask.value
      IOUtil.writeToFile(dataTask.value, outFile)

      streams.value.log.info("Wrote dependency graph to '%s'" format outFile)
      outFile
    }

  def absoluteReportPath = (file: File) ⇒ file.getAbsolutePath

  def print(key: TaskKey[String]) =
    Def.task { streams.value.log.info(key.value) }

  def printFromGraph(f: ModuleGraph ⇒ String) =
    Def.task { streams.value.log.info(f(moduleGraph.value)) }

  def showLicenseInfo(graph: ModuleGraph, streams: TaskStreams) {
    val output =
      graph.nodes.filter(_.isUsed).groupBy(_.license).toSeq.sortBy(_._1).map {
        case (license, modules) ⇒
          license.getOrElse("No license specified") + "\n" +
            modules.map("\t %s" format _.id.idString).mkString("\n")
      }.mkString("\n\n")
    streams.log.info(output)
  }

  import Project._
  val shouldForceParser: State ⇒ Parser[Boolean] = { (state: State) ⇒
    import sbt.complete.DefaultParsers._

    (Space ~> token("--force")).?.map(_.isDefined)
  }

  val artifactIdParser: Def.Initialize[State ⇒ Parser[ModuleId]] =
    resolvedScoped { ctx ⇒
      (state: State) ⇒
        val graph = loadFromContext(moduleGraphStore, ctx, state) getOrElse ModuleGraph(Nil, Nil)

        import sbt.complete.DefaultParsers._
        graph.nodes.map(_.id).map {
          case id @ ModuleId(org, name, version) ⇒
            (Space ~ token(org) ~ token(Space ~ name) ~ token(Space ~ version)).map(_ ⇒ id)
        }.reduceOption(_ | _).getOrElse {
          (Space ~> token(StringBasic, "organization") ~ Space ~ token(StringBasic, "module") ~ Space ~ token(StringBasic, "version")).map {
            case ((((org, _), mod), _), version) ⇒
              ModuleId(org, mod, version)
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
   * This is copied directly from sbt/main/Defaults.scala and then changed to update the UpdateConfiguration
   * to ignore missing artifacts.
   */
  /*def ignoreMissingUpdateT =
    ignoreMissingUpdate := {
      // FIXME: remove busywork

      val scalaVersion = Keys.scalaVersion.value
      val unmanagedScalaInstanceOnly = SbtAccess.unmanagedScalaInstanceOnly.value
      val scalaOrganization = Keys.scalaOrganization.value

      val depsUpdated = transitiveUpdate.value.exists(!_.stats.cached)
      val isRoot = executionRoots.value contains resolvedScoped.value
      val s = streams.value
      val scalaProvider = appConfiguration.value.provider.scalaProvider

      // Only substitute unmanaged jars for managed jars when the major.minor parts of the versions the same for:
      //   the resolved Scala version and the scalaHome version: compatible (weakly- no qualifier checked)
      //   the resolved Scala version and the declared scalaVersion: assume the user intended scalaHome to override anything with scalaVersion
      def subUnmanaged(subVersion: String, jars: Seq[File]) = (sv: String) ⇒
        (partialVersion(sv), partialVersion(subVersion), partialVersion(scalaVersion)) match {
          case (Some(res), Some(sh), _) if res == sh     ⇒ jars
          case (Some(res), _, Some(decl)) if res == decl ⇒ jars
          case _                                         ⇒ Nil
        }
      val subScalaJars: String ⇒ Seq[File] = unmanagedScalaInstanceOnly match {
        case Some(si) ⇒ subUnmanaged(si.version, si.allJars)
        case None     ⇒ sv ⇒ if (scalaProvider.version == sv) scalaProvider.jars else Nil
      }
      val transform: UpdateReport ⇒ UpdateReport = r ⇒ Classpaths.substituteScalaFiles(scalaOrganization, r)(subScalaJars)

      val show = Reference.display(thisProjectRef.value)
      SbtAccess.cachedUpdater(
        s.cacheDirectory, show, ivyModule.value, (updateConfiguration in ignoreMissingUpdate).value, transform, skip = (skip in update).value, force = isRoot, depsUpdated = depsUpdated, log = s.log)
    }*/
}
