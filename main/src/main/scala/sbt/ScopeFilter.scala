/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.{ Load, LoadedBuildUnit }
import sbt.internal.util.{ AttributeKey, Dag }
import sbt.librarymanagement.{ ConfigRef, Configuration }
import sbt.internal.util.Types.const
import Def.Initialize
import sbt.Project.inScope
import java.net.URI

sealed abstract class ScopeFilter { self =>

  /** Implements this filter. */
  private[ScopeFilter] def apply(data: ScopeFilter.Data): Set[Scope]

  /** Constructs a filter that selects values that match this filter but not `other`. */
  def --(other: ScopeFilter): ScopeFilter = this && -other

  /** Constructs a filter that selects values that match this filter and `other`. */
  def &&(other: ScopeFilter): ScopeFilter = new ScopeFilter:
    def apply(data: ScopeFilter.Data): Set[Scope] = self(data).intersect(other(data))

  /** Constructs a filter that selects values that match this filter or `other`. */
  def ||(other: ScopeFilter): ScopeFilter = new ScopeFilter:
    def apply(data: ScopeFilter.Data): Set[Scope] = self(data) ++ other(data)

  /** Constructs a filter that selects values that do not match this filter. */
  def unary_- : ScopeFilter = new ScopeFilter:
    def apply(data: ScopeFilter.Data): Set[Scope] = data.allScopes.set -- self(data)
}

object ScopeFilter {
  type ProjectFilter = AxisFilter[Reference]
  type ConfigurationFilter = AxisFilter[ConfigKey]
  type TaskFilter = AxisFilter[AttributeKey[_]]

  private type ScopeMap = Map[
    ScopeAxis[Reference],
    Map[
      ScopeAxis[ConfigKey],
      Map[ScopeAxis[AttributeKey[_]], Set[Scope]]
    ]
  ]

  /**
   * Construct a Scope filter from a sequence of individual scopes.
   */
  def in(scopes: Seq[Scope]): ScopeFilter =
    val scopeSet = scopes.toSet
    new ScopeFilter:
      def apply(data: Data): Set[Scope] = data.allScopes.set.intersect(scopeSet)

  /**
   * Constructs a Scope filter from filters for the individual axes.
   * If a project filter is not supplied, the enclosing project is selected.
   * If a configuration filter is not supplied, global is selected.
   * If a task filter is not supplied, global is selected.
   * Generally, always specify the project axis.
   */
  def apply(
      projects: ProjectFilter = inProjects(ThisProject),
      configurations: ConfigurationFilter = zeroAxis,
      tasks: TaskFilter = zeroAxis
  ): ScopeFilter =
    new ScopeFilter:
      def apply(data: Data): Set[Scope] =
        val pf = projects(data)
        val cf = configurations(data)
        val tf = tasks(data)
        val res =
          for {
            (project, configs) <- data.allScopes.grouped.iterator if pf(project)
            (config, tasks) <- configs.iterator if cf(config)
            (task, scopes) <- tasks.iterator if tf(task)
            scope <- scopes
          } yield scope
        res.toSet

  def debug(delegate: ScopeFilter): ScopeFilter =
    new ScopeFilter:
      def apply(data: Data): Set[Scope] =
        val res = delegate(data)
        println(s"ACCEPT $res")
        res

  final class SettingKeyAll[A] private[sbt] (i: Initialize[A]):
    /**
     * Evaluates the initialization in all scopes selected by the filter.  These are dynamic dependencies, so
     * static inspections will not show them.
     */
    def all(sfilter: => ScopeFilter): Initialize[Seq[A]] = Def.flatMap(getData) { data =>
      sfilter(data).toSeq.map(s => Project.inScope(s, i)).join
    }

  final class TaskKeyAll[A] private[sbt] (i: Initialize[Task[A]]):
    /**
     * Evaluates the task in all scopes selected by the filter.  These are dynamic dependencies, so
     * static inspections will not show them.
     */
    def all(sfilter: => ScopeFilter): Initialize[Task[Seq[A]]] = Def.flatMap(getData) { data =>
      import std.TaskExtra._
      sfilter(data).toSeq.map(s => Project.inScope(s, i)).join(_.join)
    }

  private[sbt] val Make = new Make {}
  trait Make {

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
      selectAxis(const { case _: ProjectRef => true; case _ => false })

    /** Accepts all values for the task axis except Zero. */
    def inAnyTask: TaskFilter = selectAny[AttributeKey[_]]

    /** Accepts all values for the configuration axis except Zero. */
    def inAnyConfiguration: ConfigurationFilter = selectAny[ConfigKey]

    /**
     * Selects Scopes that have a project axis that is aggregated by `ref`, transitively if `transitive` is true.
     * If `includeRoot` is true, Scopes with `ref` itself as the project axis value are also selected.
     */
    def inAggregates(
        ref: ProjectReference,
        transitive: Boolean = true,
        includeRoot: Boolean = true
    ): ProjectFilter =
      byDeps(
        ref,
        transitive = transitive,
        includeRoot = includeRoot,
        aggregate = true,
        classpath = false
      )

    /**
     * Selects Scopes that have a project axis that is a dependency of `ref`, transitively if `transitive` is true.
     * If `includeRoot` is true, Scopes with `ref` itself as the project axis value are also selected.
     */
    def inDependencies(
        ref: ProjectReference,
        transitive: Boolean = true,
        includeRoot: Boolean = true
    ): ProjectFilter =
      byDeps(
        ref,
        transitive = transitive,
        includeRoot = includeRoot,
        aggregate = false,
        classpath = true
      )

    /** Selects Scopes that have a project axis with one of the provided values. */
    def inProjects(projects: ProjectReference*): ProjectFilter =
      ScopeFilter.inProjects(projects: _*)

    /** Selects Scopes that have a task axis with one of the provided values. */
    def inTasks(tasks: Scoped*): TaskFilter = {
      val ts = tasks.map(_.key).toSet
      selectAxis[AttributeKey[_]](const(ts))
    }

    /** Selects Scopes that have a task axis with one of the provided values. */
    def inConfigurations(configs: Configuration*): ConfigurationFilter = {
      val cs = configs.map(_.name).toSet
      selectAxis[ConfigKey](const(c => cs(c.name)))
    }

    def inConfigurationsByKeys(keys: ConfigKey*): ConfigurationFilter = {
      val cs = keys.toSet
      selectAxis[ConfigKey](const(cs))
    }

    def inConfigurationsByRefs(refs: ConfigRef*): ConfigurationFilter = {
      val cs = refs.map(r => ConfigKey(r.name)).toSet
      selectAxis[ConfigKey](const(cs))
    }

    implicit def settingKeyAll[T](key: Initialize[T]): SettingKeyAll[T] =
      new SettingKeyAll[T](key)
    implicit def taskKeyAll[T](key: Initialize[Task[T]]): TaskKeyAll[T] = new TaskKeyAll[T](key)
  }

  private[sbt] final class AllScopes(val set: Set[Scope], val grouped: ScopeMap)

  /**
   * Information provided to Scope filters.  These provide project relationships,
   * project reference resolution, and the list of all static Scopes.
   */
  private[sbt] final class Data(
      val units: Map[URI, LoadedBuildUnit],
      val resolve: ProjectReference => ProjectRef,
      val allScopes: AllScopes
  )

  private[sbt] val allScopes: Initialize[AllScopes] = Def.setting {
    val scopes = Def.StaticScopes.value
    val grouped: ScopeMap =
      scopes
        .groupBy(_.project)
        .map { case (k, v) =>
          k -> v.groupBy(_.config).map { case (k, v) => k -> v.groupBy(_.task) }
        }
    new AllScopes(scopes, grouped)
  }

  /** Constructs a Data instance from the list of static scopes and the project relationships. */
  private[this] val getData: Initialize[Data] =
    Def.setting {
      val build = Keys.loadedBuild.value
      val scopes = Keys.allScopes.value
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

  private[this] def getDependencies(
      structure: Map[URI, LoadedBuildUnit],
      classpath: Boolean,
      aggregate: Boolean
  ): ProjectRef => Seq[ProjectRef] =
    ref =>
      import sbt.ProjectExtra.getProject
      Project.getProject(ref, structure).toList flatMap { p =>
        (if (classpath) p.dependencies.map(_.project) else Nil) ++
          (if (aggregate) p.aggregate else Nil)
      }

  private[this] def byDeps(
      ref: ProjectReference,
      transitive: Boolean,
      includeRoot: Boolean,
      aggregate: Boolean,
      classpath: Boolean
  ): ProjectFilter =
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
    private[sbt] def apply(data: Data): ScopeAxis[T] => Boolean = _ == Zero
  }
  private[this] def selectAny[T]: AxisFilter[T] = selectAxis(const(const(true)))
  private[this] def selectAxis[T](f: Data => T => Boolean): AxisFilter[T] = new AxisFilter[T] {
    private[sbt] def apply(data: Data): ScopeAxis[T] => Boolean = {
      val g = f(data)
      _ match {
        case Select(t) => g(t)
        case _         => false
      }
    }
  }

  /** Base functionality for filters on axis of type `In` that need access to build data. */
  sealed abstract class AxisFilter[In] { self =>

    /** Implements this filter. */
    private[ScopeFilter] def apply(data: Data): ScopeAxis[In] => Boolean

    /** Constructs a filter that selects values that match this filter but not `other`. */
    def --(other: AxisFilter[In]): AxisFilter[In] = this && -other

    /** Constructs a filter that selects values that match this filter and `other`. */
    def &&(other: AxisFilter[In]): AxisFilter[In] = new AxisFilter[In] {
      private[sbt] def apply(data: Data): ScopeAxis[In] => Boolean = {
        val a = self(data)
        val b = other(data)
        s => a(s) && b(s)
      }
    }

    /** Constructs a filter that selects values that match this filter or `other`. */
    def ||(other: AxisFilter[In]): AxisFilter[In] = new AxisFilter[In] {
      private[sbt] def apply(data: Data): ScopeAxis[In] => Boolean = {
        val a = self(data)
        val b = other(data)
        s => a(s) || b(s)
      }
    }

    /** Constructs a filter that selects values that do not match this filter. */
    def unary_- : AxisFilter[In] = new AxisFilter[In] {
      private[sbt] def apply(data: Data): ScopeAxis[In] => Boolean = {
        val a = self(data)
        s => !a(s)
      }
    }
  }

}
