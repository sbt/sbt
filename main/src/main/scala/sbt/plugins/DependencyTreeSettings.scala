/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import java.io.File
import java.util.Locale

import sbt.Def.*
import sbt.Keys.*
import sbt.ProjectExtra.*
import sbt.internal.graph.*
import sbt.internal.graph.backend.SbtUpdateReport
import sbt.internal.graph.rendering.{ DagreHTML, TreeView }
import sbt.internal.librarymanagement.*
import sbt.internal.util.complete.{ Parser, Parsers }
import sbt.internal.util.complete.DefaultParsers.*
import sbt.io.IO
import sbt.io.syntax.*
import sbt.librarymanagement.*
import sbt.util.{ Level, Logger }
import scala.Console

private[sbt] object DependencyTreeSettings:
  import sjsonnew.BasicJsonProtocol.*
  import DependencyTreeKeys.*

  enum Arg:
    case Help
    case Quiet
    case Format(format: Fmt)
    case Out(out: String)
    case Append
    case Browse

  enum Fmt:
    case Tree
    case List
    case Stats
    case Json
    case Graph
    case HtmlGraph
    case Html
    case Xml

  // Parser for the supported formats
  lazy val FmtParser: Parser[Fmt] =
    (("tree" ^^^ Fmt.Tree)
      | ("list" ^^^ Fmt.List)
      | ("stats" ^^^ Fmt.Stats)
      | ("json" ^^^ Fmt.Json)
      | ("dot" ^^^ Fmt.Graph)
      | ("graph" ^^^ Fmt.Graph)
      | ("html-graph" ^^^ Fmt.HtmlGraph)
      | ("html" ^^^ Fmt.Html)
      | ("xml" ^^^ Fmt.Xml))

  lazy val ArgParser: Parser[Arg] =
    Space ~> (("help" ^^^ Arg.Help)
      | ("--help" ^^^ Arg.Help)
      | FmtParser.map(fmt => Arg.Format(fmt)))

  lazy val ArgOptionParser: Parser[Arg] =
    Space ~> (("--quiet" ^^^ Arg.Quiet)
      | ("--append" ^^^ Arg.Append)
      | ("--browse" ^^^ Arg.Browse)
      | ("--out" ~> Space ~> StringBasic)
        .map(Arg.Out(_))
        .examples("--out /tmp/deps.txt"))

  // You can have zero-or-one format and options afterwards
  lazy val ArgsParser: Parser[Seq[Arg]] =
    (ArgParser.? ~ ArgOptionParser.*).map:
      case (a, opts) => a.toList ::: opts.toList

  def usageText: String =
    s"""dependencyTree task displays the dependency graph.

USAGE
  dependencyTree [subcommand] [options]

SUBCOMMAND
  tree         Prints ascii tree (default)
  list         Prints list of all dependencies
  graph        Prints GraphViz DOT file
  dot          Same as graph
  html         Creates HTML page
  html-graph   Creates HTML page with GraphViz DOT file
  json         Prints JSON
  xml          Prints GraphML
  stats        Prints statistics for all dependencies
  help         Prints this help

OPTIONS
  --quiet      Returns the output as task value, replacing asString
  --append     Append to the output file when used with --out
  --out <file> Writes the output to the specified file;
               The file extension will influence the default subcommand
  --browse     Opens the browser when combined with graph or html subcommand
"""

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
      dependencyTreeIgnoreMissingUpdate / ivyConfiguration := Def.uncached {
        // inTask will make sure the new definition will pick up `updateOptions in dependencyTreeIgnoreMissingUpdate`
        Project.inTask(dependencyTreeIgnoreMissingUpdate, Classpaths.mkIvyConfiguration).value
      },
      dependencyTreeIgnoreMissingUpdate / ivyModule := Def.uncached {
        // concatenating & inlining ivySbt & ivyModule default task implementations, as `SbtAccess.inTask` does
        // NOT correctly force the scope when applied to `TaskKey.toTask` instances (as opposed to raw
        // implementations like `Classpaths.mkIvyConfiguration` or `Classpaths.updateTask`)
        val is = new IvySbt((dependencyTreeIgnoreMissingUpdate / ivyConfiguration).value)
        new is.Module(moduleSettings.value)
      },
      // don't fail on missing dependencies or eviction errors
      dependencyTreeIgnoreMissingUpdate / updateConfiguration := updateConfiguration.value
        .withMissingOk(true),
      dependencyTreeIgnoreMissingUpdate / evictionErrorLevel := Level.Warn,
      dependencyTreeIgnoreMissingUpdate / assumedEvictionErrorLevel := Level.Warn,
      dependencyTreeIgnoreMissingUpdate := Def.uncached {
        // inTask will make sure the new definition will pick up `ivyModule/updateConfiguration in ignoreMissingUpdate`
        Project.inTask(dependencyTreeIgnoreMissingUpdate, Classpaths.updateTask).value
      },
    )

  /**
   * DependencyTreePlugin includes these settings for Compile and Test scopes
   * to provide dependencyTree task.
   */
  lazy val baseSettings: Seq[Def.Setting[?]] =
    Seq(
      dependencyTreeCrossProjectId := CrossVersion(scalaVersion.value, scalaBinaryVersion.value)(
        projectID.value
      ),
      dependencyTreeModuleGraph0 := Def.uncached {
        val sv = scalaVersion.value
        val internalConfig = Configurations.internalMap(configuration.value)
        val g = dependencyTreeIgnoreMissingUpdate.value
          .configuration(internalConfig)
          .map(report =>
            SbtUpdateReport.fromConfigurationReport(report, dependencyTreeCrossProjectId.value)
          )
          .getOrElse(ModuleGraph.empty)
        if (dependencyTreeIncludeScalaLibrary.value) g
        else GraphTransformations.ignoreScalaLibrary(sv, g)
      },
      dependencyTreeModuleGraphStore := dependencyTreeModuleGraph0
        .storeAs(dependencyTreeModuleGraphStore)
        .triggeredBy(dependencyTreeModuleGraph0)
        .value,
      dependencyTree := (Def.inputTaskDyn {
        val s = streams.value
        val args = ArgsParser.parsed.toList
        val isHelp = args.contains(Arg.Help)
        val isQuiet = args.contains(Arg.Quiet)
        val isAppend = args.contains(Arg.Append)
        val isBrowse = args.contains(Arg.Browse)
        if isHelp then Def.task { s.log.info(usageText); "" }
        else
          val formatOpt = (args
            .collect { case Arg.Format(fmt) => fmt })
            .reverse
            .headOption
          val outFileNameOpt = (args
            .collect { case Arg.Out(out) => out })
            .reverse
            .headOption
          val outFileOpt = outFileNameOpt.map(new File(_))
          val format = (formatOpt, outFileNameOpt) match
            case (None, Some(out)) if out.endsWith(".dot")             => Fmt.Graph
            case (None, Some(out)) if out.endsWith(".html")            => Fmt.Html
            case (None, Some(out)) if out.endsWith(".xml")             => Fmt.Xml
            case (None, Some(out)) if out.endsWith(".json")            => Fmt.Json
            case (Some(Fmt.Graph), Some(out)) if out.endsWith(".html") => Fmt.HtmlGraph
            case (Some(Fmt.Graph), _) if isBrowse                      => Fmt.HtmlGraph
            case (Some(fmt), _)                                        => fmt
            case _                                                     => Fmt.Tree
          val config = configuration.value.name
          val targetDir = target.value / config / format.toString.toLowerCase(Locale.ENGLISH)
          format match
            case Fmt.Tree | Fmt.List | Fmt.Stats =>
              Def.task {
                val graph = dependencyTreeModuleGraph0.value
                val output = format match
                  case Fmt.List  => rendering.FlatList.render(_.id.idString)(graph)
                  case Fmt.Stats => rendering.Statistics.renderModuleStatsList(graph)
                  case _         => rendering.AsciiTree.asciiTree(graph, asciiGraphWidth.value)
                handleOutput(output, outFileOpt, isQuiet, isAppend, s.log)
              }
            case Fmt.Json =>
              Def.task {
                val graph = dependencyTreeModuleGraph0.value
                val output = TreeView.createJson(graph)
                handleOutput(output, outFileOpt, isQuiet, isAppend, s.log)
              }
            case Fmt.Xml =>
              Def.task {
                val graph = dependencyTreeModuleGraph0.value
                val output = rendering.GraphML.graphMLAsString(graph)
                handleOutput(output, outFileOpt, isQuiet, isAppend, s.log)
              }
            case Fmt.Html =>
              Def.task {
                val graph = dependencyTreeModuleGraph0.value
                val renderedTree = TreeView.createJson(graph)
                val outputFile = TreeView.createFile(renderedTree, targetDir)
                if isBrowse then openBrowser(outputFile.toURI)
                outputFile.getAbsolutePath
              }
            case Fmt.Graph | Fmt.HtmlGraph =>
              Def.task {
                val graph = dependencyTreeModuleGraph0.value
                val output = rendering.DOT.dotGraph(
                  graph,
                  dependencyDotHeader.value,
                  dependencyDotNodeLabel.value,
                  rendering.DOT.HTMLLabelRendering.AngleBrackets,
                  dependencyDotNodeColors.value
                )
                if format == Fmt.Graph then
                  handleOutput(output, outFileOpt, isQuiet, isAppend, s.log)
                else
                  val outputFile = DagreHTML.createFile(output, targetDir)
                  if isBrowse then openBrowser(outputFile.toURI)
                  outputFile.getAbsolutePath
              }
      }).evaluated,
      whatDependsOn := {
        val ArtifactPattern(org, name, versionFilter) = artifactPatternParser.parsed
        val graph = dependencyTreeModuleGraph0.value
        val modules =
          versionFilter match {
            case Some(version) => GraphModuleId(org, name, version) :: Nil
            case None =>
              graph.nodes.withFilter(m => m.id.organization == org && m.id.name == name).map(_.id)
          }
        val graphWidth = asciiGraphWidth.value
        val output =
          modules
            .map { module =>
              rendering.AsciiTree
                .asciiTree(GraphTransformations.reverseGraphStartingAt(graph, module), graphWidth)
            }
            .mkString("\n")
        synchronized {
          streams.value.log.info(output)
        }
        output
      },
      dependencyLicenseInfo := (Def.inputTaskDyn {
        val s = streams.value
        val args = ArgsParser.parsed.toList
        val isHelp = args.contains(Arg.Help)
        val isQuiet = args.contains(Arg.Quiet)
        if isHelp then Def.task { s.log.info(licenseInfoUsageText); "" }
        else
          val formatOpt = (args
            .collect { case Arg.Format(fmt) => fmt })
            .reverse
            .headOption
          val outFileNameOpt = (args
            .collect { case Arg.Out(out) => out })
            .reverse
            .headOption
          val outFileOpt = outFileNameOpt.map(new File(_))
          val format = (formatOpt, outFileNameOpt) match
            case (None, Some(out)) if out.endsWith(".json") => Fmt.Json
            case (Some(fmt), _)                             => fmt
            case _                                          => Fmt.Tree
          Def.task {
            val graph = dependencyTreeModuleGraph0.value
            val output = format match
              case Fmt.Json => rendering.LicenseInfo.renderJson(graph)
              case _        => rendering.LicenseInfo.render(graph)
            handleOutput(output, outFileOpt, isQuiet, appendToFile = false, s.log)
          }
      }).evaluated,
    )

  def licenseInfoUsageText: String =
    s"""dependencyLicenseInfo task displays license information for dependencies.

USAGE
  dependencyLicenseInfo [subcommand] [options]

SUBCOMMAND
  json         Prints JSON (default is text)
  help         Prints this help

OPTIONS
  --quiet      Returns the output as task value
  --out <file> Writes the output to the specified file;
               The file extension will influence the default subcommand
"""

  private def handleOutput(
      content: String,
      outputFileOpt: Option[File],
      isQuiet: Boolean,
      appendToFile: Boolean,
      log: Logger,
  ): String =
    outputFileOpt match
      case Some(output) =>
        val toWrite =
          if appendToFile && output.exists() && output.length() > 0 then "\n" + content
          else content
        IO.write(output, toWrite, IO.utf8, append = appendToFile)
        if !isQuiet then log.info(s"wrote dependencies to $output")
        output.toString
      case None =>
        if isQuiet then content
        else
          Console.out.println(content); ""

  def openBrowser(uri: URI): Unit =
    val desktop = java.awt.Desktop.getDesktop
    desktop.synchronized {
      desktop.browse(uri)
    }

  case class ArtifactPattern(organization: String, name: String, version: Option[String])

  private[plugins] def createArtifactPatternParser(
      graph: ModuleGraph
  ): Parser[ArtifactPattern] =
    graph.nodes
      .map(_.id)
      .groupBy(m => (m.organization, m.name))
      .map { case ((org, name), modules) =>
        // Empty versions cause parser token creation to fail
        val versionParsers: Seq[Parser[Option[String]]] =
          modules
            .filter(_.version.nonEmpty)
            .map { id =>
              token(Space ~> id.version).?
            }

        // Handle modules with only empty versions
        val effectiveVersionParser =
          if versionParsers.isEmpty then success(None)
          else oneOf(versionParsers)

        (Space ~> token(org) ~ token(Space ~> name) ~ effectiveVersionParser).map {
          case ((org, name), version) => ArtifactPattern(org, name, version)
        }
      }
      .reduceOption(_ | _)
      .getOrElse {
        // If the dependencyTreeModuleGraphStore couldn't be loaded because no dependency tree command was run before, we should still provide a parser for the command.
        ((Space ~> token(StringBasic, "<organization>")) ~ (Space ~> token(
          StringBasic,
          "<module>"
        )) ~ (Space ~> token(StringBasic, "<version?>")).?).map { case ((org, mod), version) =>
          ArtifactPattern(org, mod, version)
        }
      }

  val artifactPatternParser: Def.Initialize[State => Parser[ArtifactPattern]] =
    Keys.resolvedScoped { ctx => (state: State) =>
      val graph =
        Defaults.loadFromContext(dependencyTreeModuleGraphStore, ctx, state) getOrElse ModuleGraph(
          Nil,
          Nil
        )
      createArtifactPatternParser(graph)
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
end DependencyTreeSettings
