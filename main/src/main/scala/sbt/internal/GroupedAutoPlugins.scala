/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import Def.Setting
import java.net.URI

private[sbt] final class GroupedAutoPlugins(
    val all: Seq[AutoPlugin],
    val byBuild: Map[URI, Seq[AutoPlugin]]
) {
  def globalSettings: Seq[Setting[?]] = all.flatMap(_.globalSettings)
  def buildSettings(uri: URI): Seq[Setting[?]] =
    byBuild.getOrElse(uri, Nil).flatMap(_.buildSettings)
}

private[sbt] object GroupedAutoPlugins {
  private[sbt] def apply(units: Map[URI, LoadedBuildUnit]): GroupedAutoPlugins = {
    val byBuild: Map[URI, Seq[AutoPlugin]] =
      units.view.mapValues(unit => unit.projects.flatMap(_.autoPlugins).toSeq.distinct).toMap
    val all: Seq[AutoPlugin] = byBuild.values.toSeq.flatten.distinct
    new GroupedAutoPlugins(all, byBuild)
  }
}
