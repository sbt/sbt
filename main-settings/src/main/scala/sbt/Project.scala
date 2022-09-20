/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.util.Locale
import sbt.librarymanagement.Configuration
import sbt.Def.{ Flattened, Initialize, ScopedKey, Setting }
import sbt.internal.util.Dag
import sbt.internal.util.complete.Parser
import sbt.internal.util.complete.DefaultParsers
import Scope.{ Global, ThisScope }

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

  /**
   * Applies the given functions to this Project.
   * The second function is applied to the result of applying the first to this Project and so on.
   * The intended use is a convenience for applying default configuration provided by a plugin.
   */
  def configure(transforms: (Project => Project)*): Project =
    Function.chain(transforms)(this)

  def withId(id: String): Project = copy(id = id)

  /** Sets the base directory for this project. */
  def in(dir: File): Project = copy(base = dir)

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

  private[sbt] def resolveBuild(resolveRef: ProjectReference => ProjectReference): Project =
    def resolveRefs(prs: Seq[ProjectReference]) = prs map resolveRef
    def resolveDeps(ds: Seq[ClasspathDep[ProjectReference]]) = ds map resolveDep
    def resolveDep(d: ClasspathDep[ProjectReference]) =
      ClasspathDep.ClasspathDependency(resolveRef(d.project), d.configuration)
    copy(
      aggregate = resolveRefs(aggregate),
      dependencies = resolveDeps(dependencies),
    )

  private[sbt] def resolve(resolveRef: ProjectReference => ProjectRef): ResolvedProject =
    def resolveRefs(prs: Seq[ProjectReference]) = prs.map(resolveRef)
    def resolveDeps(ds: Seq[ClasspathDep[ProjectReference]]) = ds.map(resolveDep)
    def resolveDep(d: ClasspathDep[ProjectReference]) =
      ClasspathDep.ResolvedClasspathDependency(resolveRef(d.project), d.configuration)
    Project.resolved(
      id,
      base,
      aggregate = resolveRefs(aggregate),
      dependencies = resolveDeps(dependencies),
      settings,
      configurations,
      plugins,
      autoPlugins,
      projectOrigin
    )
end Project

object Project:
  def apply(id: String, base: File): Project =
    unresolved(id, base, Nil, Nil, Nil, Nil, Plugins.empty, Nil, ProjectOrigin.Organic)

  /** This is a variation of def apply that mixes in GeneratedRootProject. */
  private[sbt] def mkGeneratedRoot(
      id: String,
      base: File,
      aggregate: Seq[ProjectReference]
  ): Project =
    validProjectID(id).foreach(errMsg => sys.error(s"Invalid project ID: $errMsg"))
    val plugins = Plugins.empty
    val origin = ProjectOrigin.GenericRoot
    new ProjectDef(id, base, aggregate, Nil, Nil, Nil, plugins, Nil, origin)
      with Project
      with GeneratedRootProject

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

  // Data structure representing an unresolved Project in terms of the project references.
  // This is created in build.sbt by the build user.
  private[sbt] def unresolved(
      id: String,
      base: File,
      aggregate: Seq[ProjectReference],
      dependencies: Seq[ClasspathDep[ProjectReference]],
      settings: Seq[Def.Setting[_]],
      configurations: Seq[Configuration],
      plugins: Plugins,
      autoPlugins: Seq[AutoPlugin],
      origin: ProjectOrigin
  ): Project =
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

  // Data structure representing resolved Project in terms of references to
  // other projects in dependencies etc.
  private def resolved(
      id: String,
      base: File,
      aggregate: Seq[ProjectRef],
      dependencies: Seq[ClasspathDep[ProjectRef]],
      settings: Seq[Def.Setting[_]],
      configurations: Seq[Configuration],
      plugins: Plugins,
      autoPlugins: Seq[AutoPlugin],
      origin: ProjectOrigin
  ): ResolvedProject =
    new ProjectDef[ProjectRef](
      id,
      base,
      aggregate,
      dependencies,
      settings,
      configurations,
      plugins,
      autoPlugins,
      origin
    ) with ResolvedProject

  /** Returns None if `id` is a valid Project ID or Some containing the parser error message if it is not. */
  def validProjectID(id: String): Option[String] =
    DefaultParsers.parse(id, DefaultParsers.ID).left.toOption

  private[this] def validProjectIDStart(id: String): Boolean =
    DefaultParsers.parse(id, DefaultParsers.IDStart).isRight

  def fillTaskAxis(scoped: ScopedKey[_]): ScopedKey[_] =
    ScopedKey(Scope.fillTaskAxis(scoped.scope, scoped.key), scoped.key)

  def mapScope(f: Scope => Scope): [a] => ScopedKey[a] => ScopedKey[a] =
    [a] => (k: ScopedKey[a]) => ScopedKey(f(k.scope), k.key)

  def transform(g: Scope => Scope, ss: Seq[Def.Setting[_]]): Seq[Def.Setting[_]] =
    val f = mapScope(g)
    ss.map { setting =>
      setting.mapKey(f).mapReferenced(f)
    }

  def transformRef(g: Scope => Scope, ss: Seq[Def.Setting[_]]): Seq[Def.Setting[_]] =
    val f = mapScope(g)
    ss.map(_ mapReferenced f)

  def inThisBuild(ss: Seq[Setting[_]]): Seq[Setting[_]] =
    inScope(ThisScope.copy(project = Select(ThisBuild)))(ss)

  private[sbt] def inThisBuild[T](i: Initialize[T]): Initialize[T] =
    inScope(ThisScope.copy(project = Select(ThisBuild)), i)

  private[sbt] def inConfig[T](conf: Configuration, i: Initialize[T]): Initialize[T] =
    inScope(ThisScope.copy(config = Select(conf)), i)

  def inTask(t: Scoped)(ss: Seq[Setting[_]]): Seq[Setting[_]] =
    inScope(ThisScope.copy(task = Select(t.key)))(ss)

  private[sbt] def inTask[A](t: Scoped, i: Initialize[A]): Initialize[A] =
    inScope(ThisScope.copy(task = Select(t.key)), i)

  def inScope(scope: Scope)(ss: Seq[Setting[_]]): Seq[Setting[_]] =
    Project.transform(Scope.replaceThis(scope), ss)

  private[sbt] def inScope[A](scope: Scope, i: Initialize[A]): Initialize[A] =
    i.mapReferenced(Project.mapScope(Scope.replaceThis(scope)))

  /**
   * Normalize a String so that it is suitable for use as a dependency management module identifier.
   * This is a best effort implementation, since valid characters are not documented or consistent.
   */
  def normalizeModuleID(id: String): String = normalizeBase(id)

  /** Constructs a valid Project ID based on `id` and returns it in Right or returns the error message in Left if one cannot be constructed. */
  private[sbt] def normalizeProjectID(id: String): Either[String, String] = {
    val attempt = normalizeBase(id)
    val refined =
      if (attempt.length < 1) "root"
      else if (!validProjectIDStart(attempt.substring(0, 1))) "root-" + attempt
      else attempt
    validProjectID(refined).toLeft(refined)
  }

  private[this] def normalizeBase(s: String) =
    s.toLowerCase(Locale.ENGLISH).replaceAll("""\W+""", "-")

  private[sbt] enum LoadAction:
    case Return
    case Current
    case Plugins

  private[sbt] lazy val loadActionParser: Parser[LoadAction] = {
    import DefaultParsers.*
    token(
      Space ~> ("plugins" ^^^ LoadAction.Plugins | "return" ^^^ LoadAction.Return)
    ) ?? LoadAction.Current
  }
end Project

sealed trait ResolvedProject extends ProjectDefinition[ProjectRef] {

  /** The [[AutoPlugin]]s enabled for this project as computed from [[plugins]]. */
  def autoPlugins: Seq[AutoPlugin]

}

private[sbt] trait GeneratedRootProject
