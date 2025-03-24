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
import java.net.URI
import sbt.internal.graph.*
import sbt.Def.*
import sbt.librarymanagement.{ ModuleID, UpdateReport }

trait MiniDependencyTreeKeys {
  val dependencyTreeIncludeScalaLibrary = settingKey[Boolean](
    "Specifies if scala dependency should be included in dependencyTree output"
  )
  val dependencyTree = taskKey[Unit]("Prints an ascii tree of all the dependencies to the console")
  val asString = taskKey[String]("Provides the string value for the task it is scoped for")
  // val printToConsole = TaskKey[Unit]("printToConsole", "Prints the tasks value to the console")
  val toFile = inputKey[File]("Writes the task value to the given file")

  // internal
  private[sbt] val dependencyTreeIgnoreMissingUpdate =
    taskKey[UpdateReport]("update used for dependencyTree task")
  private[sbt] val dependencyTreeModuleGraphStore =
    taskKey[ModuleGraph]("The stored module-graph from the last run")
  val whatDependsOn = inputKey[String]("Shows information about what depends on the given module")
  private[sbt] val dependencyTreeCrossProjectId = settingKey[ModuleID]("")
}

object MiniDependencyTreeKeys extends MiniDependencyTreeKeys

abstract class DependencyTreeKeys {
  val dependencyGraphMLFile =
    settingKey[File]("The location the graphml file should be generated at")
  val dependencyGraphML =
    taskKey[File]("Creates a graphml file containing the dependency-graph for a project")
  val dependencyDotFile =
    settingKey[File]("The location the dot file should be generated at")
  val dependencyDotNodeColors = settingKey[Boolean](
    "The boxes of nodes are painted with colors. Otherwise they're black."
  )
  val dependencyDotNodeLabel = settingKey[(String, String, String) => String](
    "Returns a formatted string of a dependency. Takes organization, name and version as parameters"
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
  // 0 was added to avoid conflict with sbt-dependency-tree
  private[sbt] val dependencyTreeModuleGraph0 =
    taskKey[ModuleGraph]("The dependency graph for a project")

  val dependencyList =
    taskKey[Unit]("Prints a list of all dependencies to the console")
  val dependencyStats =
    taskKey[Unit]("Prints statistics for all dependencies to the console")
  val dependencyLicenseInfo = taskKey[Unit](
    "Aggregates and shows information about the licenses of dependencies"
  )
}

object DependencyTreeKeys extends DependencyTreeKeys
