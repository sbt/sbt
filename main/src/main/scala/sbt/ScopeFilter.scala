/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import sbt.internal.{ Load, LoadedBuildUnit }
import sbt.internal.util.{ AttributeKey, Dag, Types }

import sbt.librarymanagement.Configuration

import Types.const
import Def.Initialize
import java.net.URI

object ScopeFilter {
  type ScopeFilter = Base[Scope]
  type AxisFilter[T] = Base[ScopeAxis[T]]
  type ProjectFilter = AxisFilter[Reference]
  type ConfigurationFilter = AxisFilter[ConfigKey]
  type TaskFilter = AxisFilter[AttributeKey[_]]

  /**
   * Constructs a Scope filter from filters for the individual axes.
   * If a project filter is not supplied, the enclosing project is selected.
   * If a configuration filter is not supplied, global is selected.
   * If a task filter is not supplied, global is selected.
   * Generally, always specify the project axis.
   */
  def apply(projects: ProjectFilter = inProjects(ThisProject),
            configurations: ConfigurationFilter = zeroAxis,
            tasks: TaskFilter = zeroAxis): ScopeFilter =
    new ScopeFilter {
      private[sbt] def apply(data: Data): Scope => Boolean = {
        val pf = projects(data)
        val cf = configurations(data)
        val tf = tasks(data)
        s =>
          pf(s.project) && cf(s.config) && tf(s.task)
      }
    }

  def debug(delegate: ScopeFilter): ScopeFilter =
    new ScopeFilter {
      private[sbt] def apply(data: Data): Scope => Boolean = {
        val d = delegate(data)
        scope =>
          {
            val accept = d(scope)
            println((if (accept) "ACCEPT " else "reject ") + scope)
            accept
          }
      }
    }

  final class SettingKeyAll[T] private[sbt] (i: Initialize[T]) {

    /**
     * Evaluates the initialization in all scopes selected by the filter.  These are dynamic dependencies, so
     * static inspections will not show them.
     */
    def all(sfilter: => ScopeFilter): Initialize[Seq[T]] = Def.bind(getData) { data =>
      data.allScopes.toSeq.filter(sfilter(data)).map(s => Project.inScope(s, i)).join
    }
  }
  final class TaskKeyAll[T] private[sbt] (i: Initialize[Task[T]]) {

    /**
     * Evaluates the task in all scopes selected by the filter.  These are dynamic dependencies, so
     * static inspections will not show them.
     */
    def all(sfilter: => ScopeFilter): Initialize[Task[Seq[T]]] = Def.bind(getData) { data =>
      import std.TaskExtra._
      data.allScopes.toSeq.filter(sfilter(data)).map(s => Project.inScope(s, i)).join(_.join)
    }
  }

  private[sbt] val Make = new Make {}
  trait Make {

    /** Selects the Scopes used in `<key>.all(<ScopeFilter>)`.*/
    type ScopeFilter = Base[Scope]

    /** Selects Scopes with a Zero task axis. */
    def inZeroTask: TaskFilter = zeroAxis[AttributeKey[_]]

    @deprecated("Use inZeroTask", "1.0.0")
    def inGlobalTask: TaskFilter = inZeroTask

    /** Selects Scopes with a Zero project axis. */
    def inZeroProject: ProjectFilter = zeroAxis[Reference]

    @deprecated("Use inZeroProject", "1.0.0")
    def inGlobalProject: ProjectFilter = inZeroProject

    /** Selects Scopes with a Zero configuration axis. */
    def inZeroConfiguration: ConfigurationFilter = zeroAxis[ConfigKey]

    @deprecated("Use inZeroConfiguration", "1.0.0")
    def inGlobalConfiguration: ConfigurationFilter = inZeroConfiguration

    /** Selects all scopes that apply to a single project. Zero and build-level scopes are excluded. */
    def inAnyProject: ProjectFilter =
      selectAxis(const { case p: ProjectRef => true; case _ => false })

    /** Accepts all values for the task axis except Zero. */
    def inAnyTask: TaskFilter = selectAny[AttributeKey[_]]

    /** Accepts all values for the configuration axis except Zero. */
    def inAnyConfiguration: ConfigurationFilter = selectAny[ConfigKey]

    /**
     * Selects Scopes that have a project axis that is aggregated by `ref`, transitively if `transitive` is true.
     * If `includeRoot` is true, Scopes with `ref` itself as the project axis value are also selected.
     */
    def inAggregates(ref: ProjectReference,
                     transitive: Boolean = true,
                     includeRoot: Boolean = true): ProjectFilter =
      byDeps(ref,
             transitive = transitive,
             includeRoot = includeRoot,
             aggregate = true,
             classpath = false)

    /**
     * Selects Scopes that have a project axis that is a dependency of `ref`, transitively if `transitive` is true.
     * If `includeRoot` is true, Scopes with `ref` itself as the project axis value are also selected.
     */
    def inDependencies(ref: ProjectReference,
                       transitive: Boolean = true,
                       includeRoot: Boolean = true): ProjectFilter =
      byDeps(ref,
             transitive = transitive,
             includeRoot = includeRoot,
             aggregate = false,
             classpath = true)

    /** Selects Scopes that have a project axis with one of the provided values.*/
    def inProjects(projects: ProjectReference*): ProjectFilter =
      ScopeFilter.inProjects(projects: _*)

    /** Selects Scopes that have a task axis with one of the provided values.*/
    def inTasks(tasks: Scoped*): TaskFilter = {
      val ts = tasks.map(_.key).toSet
      selectAxis[AttributeKey[_]](const(ts))
    }

    /** Selects Scopes that have a task axis with one of the provided values.*/
    def inConfigurations(configs: Configuration*): ConfigurationFilter = {
      val cs = configs.map(_.name).toSet
      selectAxis[ConfigKey](const(c => cs(c.name)))
    }

    implicit def settingKeyAll[T](key: Initialize[T]): SettingKeyAll[T] = new SettingKeyAll[T](key)
    implicit def taskKeyAll[T](key: Initialize[Task[T]]): TaskKeyAll[T] = new TaskKeyAll[T](key)
  }

  /**
   * Information provided to Scope filters.  These provide project relationships,
   * project reference resolution, and the list of all static Scopes.
   */
  private final class Data(val units: Map[URI, LoadedBuildUnit],
                           val resolve: ProjectReference => ProjectRef,
                           val allScopes: Set[Scope])

  /** Constructs a Data instance from the list of static scopes and the project relationships.*/
  private[this] val getData: Initialize[Data] =
    Def.setting {
      val build = Keys.loadedBuild.value
      val scopes = Def.StaticScopes.value
      val thisRef = Keys.thisProjectRef.?.value
      val current = thisRef match {
        case Some(ProjectRef(uri, _)) => uri
        case None                     => build.root
      }
      val rootProject = Load.getRootProject(build.units)
      val resolve: ProjectReference => ProjectRef = p =>
        (p, thisRef) match {
          case (ThisProject, Some(pref)) => pref
          case _                         => Scope.resolveProjectRef(current, rootProject, p)
      }
      new Data(build.units, resolve, scopes)
    }

  private[this] def getDependencies(structure: Map[URI, LoadedBuildUnit],
                                    classpath: Boolean,
                                    aggregate: Boolean): ProjectRef => Seq[ProjectRef] =
    ref =>
      Project.getProject(ref, structure).toList flatMap { p =>
        (if (classpath) p.dependencies.map(_.project) else Nil) ++
          (if (aggregate) p.aggregate else Nil)
    }

  private[this] def byDeps(ref: ProjectReference,
                           transitive: Boolean,
                           includeRoot: Boolean,
                           aggregate: Boolean,
                           classpath: Boolean): ProjectFilter =
    inResolvedProjects { data =>
      val resolvedRef = data.resolve(ref)
      val direct = getDependencies(data.units, classpath = classpath, aggregate = aggregate)
      if (transitive) {
        val full = Dag.topologicalSort(resolvedRef)(direct)
        if (includeRoot) full else full dropRight 1
      } else {
        val directDeps = direct(resolvedRef)
        if (includeRoot) resolvedRef +: directDeps else directDeps
      }
    }

  private def inProjects(projects: ProjectReference*): ProjectFilter =
    inResolvedProjects(data => projects.map(data.resolve))

  private[this] def inResolvedProjects(projects: Data => Seq[ProjectRef]): ProjectFilter =
    selectAxis(data => projects(data).toSet)

  private[this] def zeroAxis[T]: AxisFilter[T] = new AxisFilter[T] {
    private[sbt] def apply(data: Data): ScopeAxis[T] => Boolean =
      _ == Zero
  }
  private[this] def selectAny[T]: AxisFilter[T] = selectAxis(const(const(true)))
  private[this] def selectAxis[T](f: Data => T => Boolean): AxisFilter[T] = new AxisFilter[T] {
    private[sbt] def apply(data: Data): ScopeAxis[T] => Boolean = {
      val g = f(data)
      s =>
        s match {
          case Select(t) => g(t)
          case _         => false
        }
    }
  }

  /** Base functionality for filters on values of type `In` that need access to build data.*/
  sealed abstract class Base[In] { self =>

    /** Implements this filter. */
    private[ScopeFilter] def apply(data: Data): In => Boolean

    /** Constructs a filter that selects values that match this filter but not `other`.*/
    def --(other: Base[In]): Base[In] = this && -other

    /** Constructs a filter that selects values that match this filter and `other`.*/
    def &&(other: Base[In]): Base[In] = new Base[In] {
      private[sbt] def apply(data: Data): In => Boolean = {
        val a = self(data)
        val b = other(data)
        s =>
          a(s) && b(s)
      }
    }

    /** Constructs a filter that selects values that match this filter or `other`.*/
    def ||(other: Base[In]): Base[In] = new Base[In] {
      private[sbt] def apply(data: Data): In => Boolean = {
        val a = self(data)
        val b = other(data)
        s =>
          a(s) || b(s)
      }
    }

    /** Constructs a filter that selects values that do not match this filter.*/
    def unary_- : Base[In] = new Base[In] {
      private[sbt] def apply(data: Data): In => Boolean = {
        val a = self(data)
        s =>
          !a(s)
      }
    }
  }

}
