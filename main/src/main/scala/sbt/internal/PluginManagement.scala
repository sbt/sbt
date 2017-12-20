/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import Keys.Classpath
import Def.Setting
import PluginManagement._
import sbt.librarymanagement.ModuleID

import java.net.{ URI, URL, URLClassLoader }

final case class PluginManagement(overrides: Set[ModuleID],
                                  applyOverrides: Set[ModuleID],
                                  loader: PluginClassLoader,
                                  initialLoader: ClassLoader,
                                  context: Context) {
  def shift: PluginManagement =
    PluginManagement(Set.empty,
                     overrides,
                     new PluginClassLoader(initialLoader),
                     initialLoader,
                     context)

  def addOverrides(os: Set[ModuleID]): PluginManagement =
    copy(overrides = overrides ++ os)

  def addOverrides(cp: Classpath): PluginManagement =
    addOverrides(extractOverrides(cp))

  def inject: Seq[Setting[_]] = Seq(
    Keys.dependencyOverrides ++= overrides.toVector
  )

  def resetDepth: PluginManagement =
    copy(context = Context(globalPluginProject = false, pluginProjectDepth = 0))
  def forGlobalPlugin: PluginManagement =
    copy(context = Context(globalPluginProject = true, pluginProjectDepth = 0))
  def forPlugin: PluginManagement =
    copy(context = context.copy(pluginProjectDepth = context.pluginProjectDepth + 1))
}
object PluginManagement {
  final case class Context private[sbt] (globalPluginProject: Boolean, pluginProjectDepth: Int)
  val emptyContext: Context = Context(false, 0)

  def apply(initialLoader: ClassLoader): PluginManagement =
    PluginManagement(Set.empty,
                     Set.empty,
                     new PluginClassLoader(initialLoader),
                     initialLoader,
                     emptyContext)

  def extractOverrides(classpath: Classpath): Set[ModuleID] =
    classpath flatMap { _.metadata get Keys.moduleID.key map keepOverrideInfo } toSet;

  def keepOverrideInfo(m: ModuleID): ModuleID =
    ModuleID(m.organization, m.name, m.revision).withCrossVersion(m.crossVersion)

  final class PluginClassLoader(p: ClassLoader) extends URLClassLoader(Array(), p) {
    private[this] val urlSet = new collection.mutable.HashSet[URI] // remember: don't use hashCode/equals on URL
    def add(urls: Seq[URL]): Unit = synchronized {
      for (url <- urls)
        if (urlSet.add(url.toURI))
          addURL(url)
    }
  }
}
