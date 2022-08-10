/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import sbt.librarymanagement.Configuration
import sbt.Def.{ Flattened, Initialize, ScopedKey, Setting }
import sbt.internal.util.Dag
import sbt.internal.util.complete.DefaultParsers

sealed trait ProjectDefinition[PR <: ProjectReference] {

  /**
   * The project ID is used to uniquely identify a project within a build.
   * It is used to refer to a project from the command line and in the scope of keys.
   */
  def id: String

  /** The base directory for the project. */
  def base: File

  /**
   * The configurations for this project.  These are groups of related tasks and the main reason
   * to list them here is when one configuration extends another.  In this case, a setting lookup
   * in one configuration will fall back to the configurations it extends configuration if the setting doesn't exist.
   */
  def configurations: Seq[Configuration]

  /**
   * The explicitly defined sequence of settings that configure this project.
   * These do not include the automatically appended settings as configured by `auto`.
   */
  def settings: Seq[Setting[_]]

  /**
   * The references to projects that are aggregated by this project.
   * When a task is run on this project, it will also be run on aggregated projects.
   */
  def aggregate: Seq[PR]

  /** The references to projects that are classpath dependencies of this project. */
  def dependencies: Seq[ClasspathDep[PR]]

  /** The references to projects that are aggregate and classpath dependencies of this project. */
  def uses: Seq[PR] = aggregate ++ dependencies.map(_.project)
  def referenced: Seq[PR] = uses

  /**
   * The defined [[Plugins]] associated with this project.
   * A [[AutoPlugin]] is a common label that is used by plugins to determine what settings, if any, to add to a project.
   */
  def plugins: Plugins

  /** Indicates whether the project was created organically, or was generated synthetically. */
  def projectOrigin: ProjectOrigin

  /** The [[AutoPlugin]]s enabled for this project.  This value is only available on a loaded Project. */
  private[sbt] def autoPlugins: Seq[AutoPlugin]

  override final def hashCode: Int = id.hashCode ^ base.hashCode ^ getClass.hashCode

  override final def equals(o: Any) = o match {
    case p: ProjectDefinition[_] => p.getClass == this.getClass && p.id == id && p.base == base
    case _                       => false
  }

  override def toString = {
    val agg = ifNonEmpty("aggregate", aggregate)
    val dep = ifNonEmpty("dependencies", dependencies)
    val conf = ifNonEmpty("configurations", configurations)
    val autos = ifNonEmpty("autoPlugins", autoPlugins.map(_.label))
    val fields =
      s"id $id" :: s"base: $base" :: agg ::: dep ::: conf ::: (s"plugins: List($plugins)" :: autos)
    s"Project(${fields.mkString(", ")})"
  }

  private[this] def ifNonEmpty[T](label: String, ts: Iterable[T]): List[String] =
    if (ts.isEmpty) Nil else s"$label: $ts" :: Nil
}

trait CompositeProject:
  def componentProjects: Seq[Project]
end CompositeProject

private[sbt] object CompositeProject {

  /**
   *  Expand user defined projects with the component projects of `compositeProjects`.
   *
   *  If two projects with the same id appear in the user defined projects and
   *  in `compositeProjects.componentProjects`, the user defined project wins.
   *  This is necessary for backward compatibility with the idioms:
   *  {{{
   *    lazy val foo = crossProject
   *    lazy val fooJS = foo.js.settings(...)
   *    lazy val fooJVM = foo.jvm.settings(...)
   *  }}}
   *  and the rarer:
   *  {{{
   *    lazy val fooJS = foo.js.settings(...)
   *    lazy val foo = crossProject
   *    lazy val fooJVM = foo.jvm.settings(...)
   *  }}}
   */
  def expand(compositeProjects: Seq[CompositeProject]): Seq[Project] = {
    val userProjects = compositeProjects.collect { case p: Project => p }
    for (p <- compositeProjects.flatMap(_.componentProjects)) yield {
      userProjects.find(_.id == p.id) match {
        case Some(userProject) => userProject
        case None              => p
      }
    }
  }.distinct
}

sealed trait Project extends ProjectDefinition[ProjectReference] with CompositeProject:
  override def componentProjects: Seq[Project] = this :: Nil

  /** Adds new configurations directly to this project.  To override an existing configuration, use `overrideConfigs`. */
  def configs(cs: Configuration*): Project = copy(configurations = configurations ++ cs)

  /** Adds classpath dependencies on internal or external projects. */
  def dependsOn(deps: ClasspathDep[ProjectReference]*): Project =
    copy(dependencies = dependencies ++ deps)

  /**
   * Adds projects to be aggregated.  When a user requests a task to run on this project from the command line,
   * the task will also be run in aggregated projects.
   */
  def aggregate(refs: ProjectReference*): Project =
    copy(aggregate = (aggregate: Seq[ProjectReference]) ++ refs)

  /** Appends settings to the current settings sequence for this project. */
  def settings(ss: Def.SettingsDefinition*): Project =
    copy(settings = (settings: Seq[Def.Setting[_]]) ++ Def.settings(ss: _*))

  /**
   * Sets the [[AutoPlugin]]s of this project.
   * A [[AutoPlugin]] is a common label that is used by plugins to determine what settings, if any, to enable on a project.
   */
  def enablePlugins(ns: Plugins*): Project =
    setPlugins(ns.foldLeft(plugins)(Plugins.and))

  /** Disable the given plugins on this project. */
  def disablePlugins(ps: AutoPlugin*): Project =
    setPlugins(Plugins.and(plugins, Plugins.And(ps.map(p => Plugins.Exclude(p)).toList)))

  private[sbt] def setPlugins(ns: Plugins): Project = copy(plugins = ns)

  /** Definitively set the [[AutoPlugin]]s for this project. */
  private[sbt] def setAutoPlugins(autos: Seq[AutoPlugin]): Project = copy(autoPlugins = autos)

  /** Definitively set the [[ProjectOrigin]] for this project. */
  private[sbt] def setProjectOrigin(origin: ProjectOrigin): Project = copy(projectOrigin = origin)

  private[sbt] def copy(
      id: String = id,
      base: File = base,
      aggregate: Seq[ProjectReference] = aggregate,
      dependencies: Seq[ClasspathDep[ProjectReference]] = dependencies,
      settings: Seq[Setting[_]] = settings,
      configurations: Seq[Configuration] = configurations,
      plugins: Plugins = plugins,
      autoPlugins: Seq[AutoPlugin] = autoPlugins,
      projectOrigin: ProjectOrigin = projectOrigin,
  ): Project =
    Project.unresolved(
      id,
      base,
      aggregate = aggregate,
      dependencies = dependencies,
      settings = settings,
      configurations,
      plugins,
      autoPlugins,
      projectOrigin
    )
end Project

object Project:
  private abstract class ProjectDef[PR <: ProjectReference](
      val id: String,
      val base: File,
      val aggregate: Seq[PR],
      val dependencies: Seq[ClasspathDep[PR]],
      val settings: Seq[Def.Setting[_]],
      val configurations: Seq[Configuration],
      val plugins: Plugins,
      val autoPlugins: Seq[AutoPlugin],
      val projectOrigin: ProjectOrigin
  ) extends ProjectDefinition[PR] {
    // checks for cyclic references here instead of having to do it in Scope.delegates
    Dag.topologicalSort(configurations)(_.extendsConfigs)
  }

  private def unresolved(
      id: String,
      base: File,
      aggregate: Seq[ProjectReference],
      dependencies: Seq[ClasspathDep[ProjectReference]],
      settings: Seq[Def.Setting[_]],
      configurations: Seq[Configuration],
      plugins: Plugins,
      autoPlugins: Seq[AutoPlugin],
      origin: ProjectOrigin
  ): Project = {
    validProjectID(id).foreach(errMsg => sys.error("Invalid project ID: " + errMsg))
    new ProjectDef[ProjectReference](
      id,
      base,
      aggregate,
      dependencies,
      settings,
      configurations,
      plugins,
      autoPlugins,
      origin
    ) with Project
  }

  /** Returns None if `id` is a valid Project ID or Some containing the parser error message if it is not. */
  def validProjectID(id: String): Option[String] =
    DefaultParsers.parse(id, DefaultParsers.ID).left.toOption
end Project

sealed trait ResolvedProject extends ProjectDefinition[ProjectRef] {

  /** The [[AutoPlugin]]s enabled for this project as computed from [[plugins]]. */
  def autoPlugins: Seq[AutoPlugin]

}
