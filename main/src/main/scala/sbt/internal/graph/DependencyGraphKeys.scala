/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package graph

import java.io.File
import java.net.URI
import sbt.BuildSyntax._
import sbt.librarymanagement.{ ModuleID, UpdateReport }

trait DependencyGraphKeys {
  val asString = taskKey[String]("Provides the string value for the task it is scoped for")
  // val printToConsole = TaskKey[Unit]("printToConsole", "Prints the tasks value to the console")
  val toFile = inputKey[File]("Writes the task value to the given file")

  val dependencyTreeIncludeScalaLibrary = settingKey[Boolean](
    "Specifies if scala dependency should be included in dependencyTree output"
  )

  val dependencyGraphMLFile =
    settingKey[File]("The location the graphml file should be generated at")
  val dependencyGraphML =
    taskKey[File]("Creates a graphml file containing the dependency-graph for a project")
  val dependencyDotFile =
    settingKey[File]("The location the dot file should be generated at")
  val dependencyDotNodeLabel = settingKey[(String, String, String) => String](
    "Returns a formated string of a dependency. Takes organization, name and version as parameters"
  )
  val dependencyDotHeader = settingKey[String](
    "The header of the dot file. (e.g. to set your preferred node shapes)"
  )
  val dependencyDot = taskKey[File](
    "Creates a dot file containing the dependency-graph for a project"
  )
  val dependencyDotString = taskKey[String](
    "Creates a String containing the dependency-graph for a project in dot format"
  )
  val dependencyBrowseGraphTarget = settingKey[File](
    "The location dependency browse graph files should be put."
  )
  val dependencyBrowseGraphHTML = taskKey[URI](
    "Creates an HTML page that can be used to view the graph."
  )
  val dependencyBrowseGraph = taskKey[URI](
    "Opens an HTML page that can be used to view the graph."
  )
  val dependencyBrowseTreeTarget = settingKey[File](
    "The location dependency browse tree files should be put."
  )
  val dependencyBrowseTreeHTML = taskKey[URI](
    "Creates an HTML page that can be used to view the dependency tree"
  )
  val dependencyBrowseTree = taskKey[URI](
    "Opens an HTML page that can be used to view the dependency tree"
  )
  val moduleGraph = taskKey[ModuleGraph]("The dependency graph for a project")
  val moduleGraphIvyReport = taskKey[ModuleGraph](
    "The dependency graph for a project as generated from an Ivy Report XML"
  )
  val moduleGraphSbt = taskKey[ModuleGraph](
    "The dependency graph for a project as generated from SBT data structures."
  )
  val dependencyGraph = inputKey[Unit]("Prints the ascii graph to the console")
  val dependencyTree = taskKey[Unit]("Prints an ascii tree of all the dependencies to the console")
  val dependencyList =
    taskKey[Unit]("Prints a list of all dependencies to the console")
  val dependencyStats =
    taskKey[Unit]("Prints statistics for all dependencies to the console")
  val ivyReportFunction = taskKey[String => File](
    "A function which returns the file containing the ivy report from the ivy cache for a given configuration"
  )
  val ivyReport = taskKey[File](
    "A task which returns the location of the ivy report file for a given configuration (default `compile`)."
  )
  val dependencyLicenseInfo = taskKey[Unit](
    "Aggregates and shows information about the licenses of dependencies"
  )

  // internal
  private[sbt] val ignoreMissingUpdate =
    TaskKey[UpdateReport]("dependencyUpdate", "sbt-dependency-graph version of update")
  private[sbt] val moduleGraphStore =
    TaskKey[ModuleGraph]("module-graph-store", "The stored module-graph from the last run")
  val whatDependsOn =
    InputKey[String]("what-depends-on", "Shows information about what depends on the given module")
  private[sbt] val crossProjectId = SettingKey[ModuleID]("dependency-graph-cross-project-id")
}

object DependencyGraphKeys extends DependencyGraphKeys
