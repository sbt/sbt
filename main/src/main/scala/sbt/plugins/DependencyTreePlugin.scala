/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import sbt.PluginTrigger.AllRequirements
import sbt.ProjectExtra.*
import sbt.librarymanagement.Configurations.{ Compile, Test }

object DependencyTreePlugin extends AutoPlugin {
  object autoImport extends DependencyTreeKeys

  private val defaultDependencyDotHeader =
    """|digraph "dependency-graph" {
       |    graph[rankdir="LR"; splines=polyline]
       |    edge [
       |        arrowtail="none"
       |    ]""".stripMargin

  private val defaultDependencyDotNodeLabel =
    (organization: String, name: String, version: String) =>
      s"""${organization}<BR/><B>${name}</B><BR/>${version}"""

  import autoImport.*
  override def trigger: PluginTrigger = AllRequirements
  override def globalSettings: Seq[Def.Setting[?]] = Seq(
    dependencyTreeIncludeScalaLibrary :== false,
    dependencyDotNodeColors :== true,
    dependencyDotHeader := defaultDependencyDotHeader,
    dependencyDotNodeLabel := defaultDependencyDotNodeLabel,
  )
  override lazy val projectSettings: Seq[Def.Setting[?]] =
    DependencyTreeSettings.coreSettings ++
      inConfig(Compile)(DependencyTreeSettings.baseSettings) ++
      inConfig(Test)(DependencyTreeSettings.baseSettings)
}
