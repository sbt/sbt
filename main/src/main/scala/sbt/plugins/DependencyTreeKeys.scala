/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import sbt.internal.graph.*
import sbt.Def.*
import sbt.librarymanagement.{ ModuleID, UpdateReport }

abstract class DependencyTreeKeys:
  val dependencyTree = inputKey[String]("Displays dependencies in ascii tree and other formats")
  val dependencyTreeIncludeScalaLibrary = settingKey[Boolean](
    "Specifies if scala dependency should be included in dependencyTree output"
  )
  val dependencyDotNodeColors = settingKey[Boolean](
    "The boxes of nodes are painted with colors. Otherwise they're black."
  )
  val dependencyDotNodeLabel = settingKey[(String, String, String) => String](
    "Returns a formatted string of a dependency. Takes organization, name and version as parameters"
  )
  val dependencyDotHeader = settingKey[String](
    "The header of the dot file. (e.g. to set your preferred node shapes)"
  )

  // internal
  private[sbt] val dependencyTreeIgnoreMissingUpdate =
    taskKey[UpdateReport]("update used for dependencyTree task")
  private[sbt] val dependencyTreeModuleGraphStore =
    taskKey[ModuleGraph]("The stored module-graph from the last run")
  val whatDependsOn = inputKey[String]("Shows information about what depends on the given module")
  val dependencyLicenseInfo =
    inputKey[String]("Displays license information for dependencies in text or JSON format")
  private[sbt] val dependencyTreeCrossProjectId = settingKey[ModuleID]("")

  // 0 was added to avoid conflict with sbt-dependency-tree
  private[sbt] val dependencyTreeModuleGraph0 =
    taskKey[ModuleGraph]("The dependency graph for a project")
end DependencyTreeKeys

object DependencyTreeKeys extends DependencyTreeKeys
