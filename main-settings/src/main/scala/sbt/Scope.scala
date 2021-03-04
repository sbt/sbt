/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.net.URI

import sbt.internal.util.{ AttributeKey, AttributeMap, Dag }
import sbt.internal.util.Util._

import sbt.io.IO

final case class Scope(
    project: ScopeAxis[Reference],
    config: ScopeAxis[ConfigKey],
    task: ScopeAxis[AttributeKey[_]],
    extra: ScopeAxis[AttributeMap]
) {
  @deprecated(Scope.inIsDeprecated, "1.5.0")
  def in(project: Reference, config: ConfigKey): Scope =
    copy(project = Select(project), config = Select(config))

  @deprecated(Scope.inIsDeprecated, "1.5.0")
  def in(config: ConfigKey, task: AttributeKey[_]): Scope =
    copy(config = Select(config), task = Select(task))

  @deprecated(Scope.inIsDeprecated, "1.5.0")
  def in(project: Reference, task: AttributeKey[_]): Scope =
    copy(project = Select(project), task = Select(task))

  @deprecated(Scope.inIsDeprecated, "1.5.0")
  def in(project: Reference, config: ConfigKey, task: AttributeKey[_]): Scope =
    copy(project = Select(project), config = Select(config), task = Select(task))

  @deprecated(Scope.inIsDeprecated, "1.5.0")
  def in(project: Reference): Scope = copy(project = Select(project))

  @deprecated(Scope.inIsDeprecated, "1.5.0")
  def in(config: ConfigKey): Scope = copy(config = Select(config))

  @deprecated(Scope.inIsDeprecated, "1.5.0")
  def in(task: AttributeKey[_]): Scope = copy(task = Select(task))

  override def toString: String = this match {
    case Scope(Zero, Zero, Zero, Zero) => "Global"
    case Scope(_, _, _, This)          => s"$project / $config / $task"
    case _                             => s"Scope($project, $config, $task, $extra)"
  }
}
object Scope {
  val ThisScope: Scope = Scope(This, This, This, This)
  val Global: Scope = Scope(Zero, Zero, Zero, Zero)
  val GlobalScope: Scope = Global

  private[sbt] final val inIsDeprecated =
    "`in` is deprecated; migrate to slash syntax - https://www.scala-sbt.org/1.x/docs/Migrating-from-sbt-013x.html#slash"

  def resolveScope(thisScope: Scope, current: URI, rootProject: URI => String): Scope => Scope =
    resolveProject(current, rootProject) compose replaceThis(thisScope) compose subThisProject

  def resolveBuildScope(thisScope: Scope, current: URI): Scope => Scope =
    buildResolve(current) compose replaceThis(thisScope) compose subThisProject

  def replaceThis(thisScope: Scope): Scope => Scope =
    (scope: Scope) =>
      Scope(
        subThis(thisScope.project, scope.project),
        subThis(thisScope.config, scope.config),
        subThis(thisScope.task, scope.task),
        subThis(thisScope.extra, scope.extra)
      )

  def subThis[T](sub: ScopeAxis[T], into: ScopeAxis[T]): ScopeAxis[T] =
    if (into == This) sub else into

  /**
   * `Select(ThisProject)` cannot be resolved by [[resolveProject]] (it doesn't know what to replace it with), so we
   * perform this transformation so that [[replaceThis]] picks it up.
   */
  def subThisProject: Scope => Scope = {
    case s @ Scope(Select(ThisProject), _, _, _) => s.copy(project = This)
    case s                                       => s
  }

  def fillTaskAxis(scope: Scope, key: AttributeKey[_]): Scope =
    scope.task match {
      case _: Select[_] => scope
      case _            => scope.copy(task = Select(key))
    }

  def mapReference(f: Reference => Reference): Scope => Scope = {
    case Scope(Select(ref), a, b, c) => Scope(Select(f(ref)), a, b, c)
    case x                           => x
  }
  def resolveProject(uri: URI, rootProject: URI => String): Scope => Scope =
    mapReference(ref => resolveReference(uri, rootProject, ref))
  def buildResolve(uri: URI): Scope => Scope =
    mapReference(ref => resolveBuildOnly(uri, ref))

  def resolveBuildOnly(current: URI, ref: Reference): Reference =
    ref match {
      case br: BuildReference   => resolveBuild(current, br)
      case pr: ProjectReference => resolveProjectBuild(current, pr)
    }
  def resolveBuild(current: URI, ref: BuildReference): BuildReference =
    ref match {
      case ThisBuild     => BuildRef(current)
      case BuildRef(uri) => BuildRef(resolveBuild(current, uri))
    }
  def resolveProjectBuild(current: URI, ref: ProjectReference): ProjectReference =
    ref match {
      case LocalRootProject    => RootProject(current)
      case LocalProject(id)    => ProjectRef(current, id)
      case RootProject(uri)    => RootProject(resolveBuild(current, uri))
      case ProjectRef(uri, id) => ProjectRef(resolveBuild(current, uri), id)
      case ThisProject         => ThisProject // haven't exactly "resolved" anything..
    }
  def resolveBuild(current: URI, uri: URI): URI =
    if (!uri.isAbsolute && current.isOpaque && uri.getSchemeSpecificPart == ".")
      current // this handles the shortcut of referring to the current build using "."
    else
      IO.directoryURI(current resolve uri)

  def resolveReference(
      current: URI,
      rootProject: URI => String,
      ref: Reference
  ): ResolvedReference =
    ref match {
      case br: BuildReference   => resolveBuildRef(current, br)
      case pr: ProjectReference => resolveProjectRef(current, rootProject, pr)
    }

  def resolveProjectRef(
      current: URI,
      rootProject: URI => String,
      ref: ProjectReference
  ): ProjectRef =
    ref match {
      case LocalRootProject    => ProjectRef(current, rootProject(current))
      case LocalProject(id)    => ProjectRef(current, id)
      case RootProject(uri)    => val u = resolveBuild(current, uri); ProjectRef(u, rootProject(u))
      case ProjectRef(uri, id) => ProjectRef(resolveBuild(current, uri), id)
      case ThisProject         => sys.error("Cannot resolve ThisProject w/o the current project")
    }
  def resolveBuildRef(current: URI, ref: BuildReference): BuildRef =
    ref match {
      case ThisBuild     => BuildRef(current)
      case BuildRef(uri) => BuildRef(resolveBuild(current, uri))
    }

  def display(config: ConfigKey): String = guessConfigIdent(config.name) + " /"

  private[sbt] val configIdents: Map[String, String] =
    Map(
      "it" -> "IntegrationTest",
      "scala-tool" -> "ScalaTool",
      "plugin" -> "CompilerPlugin"
    )
  private[sbt] val configIdentsInverse: Map[String, String] =
    configIdents map { _.swap }

  private[sbt] def guessConfigIdent(conf: String): String =
    configIdents.applyOrElse(conf, (x: String) => x.capitalize)

  private[sbt] def unguessConfigIdent(conf: String): String =
    configIdentsInverse.applyOrElse(conf, (x: String) => x.take(1).toLowerCase + x.drop(1))

  def displayConfigKey012Style(config: ConfigKey): String = config.name + ":"

  def display(scope: Scope, sep: String): String =
    displayMasked(scope, sep, showProject, ScopeMask())

  def display(scope: Scope, sep: String, showProject: Reference => String): String =
    displayMasked(scope, sep, showProject, ScopeMask())

  private[sbt] def displayPedantic(scope: Scope, sep: String): String =
    displayMasked(scope, sep, showProject, ScopeMask(), true)

  def displayMasked(scope: Scope, sep: String, mask: ScopeMask): String =
    displayMasked(scope, sep, showProject, mask)

  def displayMasked(scope: Scope, sep: String, mask: ScopeMask, showZeroConfig: Boolean): String =
    displayMasked(scope, sep, showProject, mask, showZeroConfig)

  def displayMasked(
      scope: Scope,
      sep: String,
      showProject: Reference => String,
      mask: ScopeMask
  ): String =
    displayMasked(scope, sep, showProject, mask, false)

  /**
   * Allows the user to override the result of `Scope.display` or `Scope.displayMasked` for a
   * particular scope. This can be used to enhance super shell and/or error reporting for tasks
   * that use mangled names. For example, one might have:
   * {{{
   *   val mangledKey = TaskKey[Unit]("foo_slash_bar")
   *   val attributeMap = AttributeMap.empty.put(Scope.customShowString("foo/bar"))
   *   val sanitizedKey = mangledKey.copy(scope = mangledKey.copy(extra = Select(attributeMap)))
   *   sanitizedKey := { ... }
   * }}}
   *
   * Now whenever the `foo_slash_bar` task specified by sanitizedKey is evaluated, it will display
   * "foo/bar" in super shell progress and in the error message if an error is thrown.
   */
  val customShowString = AttributeKey[String]("scope-custom-show-string")

  /**
   * unified slash style introduced in sbt 1.1.0.
   * By default, sbt will no longer display the Zero-config,
   * so `name` will render as `name` as opposed to `{uri}proj/Zero/name`.
   * Technically speaking an unspecified configuration axis defaults to
   * the scope delegation (first configuration defining the key, then Zero).
   */
  def displayMasked(
      scope: Scope,
      sep: String,
      showProject: Reference => String,
      mask: ScopeMask,
      showZeroConfig: Boolean
  ): String = {
    import scope.{ project, config, task, extra }
    extra.toOption.flatMap(_.get(customShowString)).getOrElse {
      val zeroConfig = if (showZeroConfig) "Zero /" else ""
      val configPrefix = config.foldStrict(display, zeroConfig, "./")
      val taskPrefix = task.foldStrict(_.label + " /", "", "./")
      val extras = extra.foldStrict(_.entries.map(_.toString).toList, nil, nil)
      val postfix = if (extras.isEmpty) "" else extras.mkString("(", ", ", ")")
      if (scope == GlobalScope) "Global / " + sep + postfix
      else
        mask.concatShow(
          appendSpace(projectPrefix(project, showProject)),
          appendSpace(configPrefix),
          appendSpace(taskPrefix),
          sep,
          postfix
        )
    }
  }

  private[sbt] def appendSpace(s: String): String =
    if (s == "") ""
    else s + " "

  def equal(a: Scope, b: Scope, mask: ScopeMask): Boolean =
    (!mask.project || a.project == b.project) &&
      (!mask.config || a.config == b.config) &&
      (!mask.task || a.task == b.task) &&
      (!mask.extra || a.extra == b.extra)

  def projectPrefix(
      project: ScopeAxis[Reference],
      show: Reference => String = showProject
  ): String =
    project.foldStrict(show, "Zero /", "./")

  def projectPrefix012Style(
      project: ScopeAxis[Reference],
      show: Reference => String = showProject
  ): String =
    project.foldStrict(show, "*/", "./")

  def showProject = (ref: Reference) => Reference.display(ref) + " /"

  def showProject012Style = (ref: Reference) => Reference.display(ref) + "/"

  @deprecated("No longer used", "1.1.3")
  def transformTaskName(s: String) = {
    val parts = s.split("-+")
    (parts.take(1) ++ parts.drop(1).map(_.capitalize)).mkString
  }

  @deprecated("Use variant without extraInherit", "1.1.1")
  def delegates[Proj](
      refs: Seq[(ProjectRef, Proj)],
      configurations: Proj => Seq[ConfigKey],
      resolve: Reference => ResolvedReference,
      rootProject: URI => String,
      projectInherit: ProjectRef => Seq[ProjectRef],
      configInherit: (ResolvedReference, ConfigKey) => Seq[ConfigKey],
      taskInherit: AttributeKey[_] => Seq[AttributeKey[_]],
      extraInherit: (ResolvedReference, AttributeMap) => Seq[AttributeMap]
  ): Scope => Seq[Scope] =
    delegates(
      refs,
      configurations,
      resolve,
      rootProject,
      projectInherit,
      configInherit,
      taskInherit,
    )

  // *Inherit functions should be immediate delegates and not include argument itself.  Transitivity will be provided by this method
  def delegates[Proj](
      refs: Seq[(ProjectRef, Proj)],
      configurations: Proj => Seq[ConfigKey],
      resolve: Reference => ResolvedReference,
      rootProject: URI => String,
      projectInherit: ProjectRef => Seq[ProjectRef],
      configInherit: (ResolvedReference, ConfigKey) => Seq[ConfigKey],
      taskInherit: AttributeKey[_] => Seq[AttributeKey[_]],
  ): Scope => Seq[Scope] = {
    val index = delegates(refs, configurations, projectInherit, configInherit)
    scope => indexedDelegates(resolve, index, rootProject, taskInherit)(scope)
  }

  @deprecated("Use variant without extraInherit", "1.1.1")
  def indexedDelegates(
      resolve: Reference => ResolvedReference,
      index: DelegateIndex,
      rootProject: URI => String,
      taskInherit: AttributeKey[_] => Seq[AttributeKey[_]],
      extraInherit: (ResolvedReference, AttributeMap) => Seq[AttributeMap]
  )(rawScope: Scope): Seq[Scope] =
    indexedDelegates(resolve, index, rootProject, taskInherit)(rawScope)

  def indexedDelegates(
      resolve: Reference => ResolvedReference,
      index: DelegateIndex,
      rootProject: URI => String,
      taskInherit: AttributeKey[_] => Seq[AttributeKey[_]],
  )(rawScope: Scope): Seq[Scope] = {
    val scope = Scope.replaceThis(GlobalScope)(rawScope)

    // This is a hot method that gets called many times
    def expandDelegateScopes(
        resolvedProj: ResolvedReference
    )(pLin: Seq[ScopeAxis[ResolvedReference]]): Vector[Scope] = {
      val tLin = scope.task match {
        case t @ Select(_) => linearize(t)(taskInherit)
        case _             => withZeroAxis(scope.task)
      }
      // val eLin = withZeroAxis(scope.extra)
      // The following while loops handroll the nested for-expression + flatMap
      // projAxes flatMap nonProjectScopes(resolvedProj)
      //   ...
      //   for (c <- cLin; t <- tLin; e <- eLin) yield Scope(px, c, t, e)
      val res = Vector.newBuilder[Scope]
      val pIt = pLin.iterator
      while (pIt.hasNext) {
        val px = pIt.next()
        val p = px.toOption getOrElse resolvedProj
        val configProj = p match {
          case pr: ProjectRef => pr
          case br: BuildRef   => ProjectRef(br.build, rootProject(br.build))
        }
        val cLin = scope.config match {
          case Select(conf) => index.config(configProj, conf)
          case _            => withZeroAxis(scope.config)
        }
        val cLinIt = cLin.iterator
        while (cLinIt.hasNext) {
          val c = cLinIt.next()
          val tLinIt = tLin.iterator
          while (tLinIt.hasNext) {
            val t = tLinIt.next()
            if (scope.extra.isSelect) {
              res += Scope(px, c, t, scope.extra)
              ()
            }
            res += Scope(px, c, t, Zero)
          }
        }
      }
      res.result()
    }

    scope.project match {
      case Zero | This => globalProjectDelegates(scope)
      case Select(proj) =>
        val resolvedProj = resolve(proj)
        val projAxes: Seq[ScopeAxis[ResolvedReference]] =
          resolvedProj match {
            case pr: ProjectRef => index.project(pr)
            case br: BuildRef =>
              List(Select(br): ScopeAxis[ResolvedReference], Zero: ScopeAxis[ResolvedReference])
          }
        expandDelegateScopes(resolvedProj)(projAxes)
    }
  }

  private val zeroL = List(Zero)
  def withZeroAxis[T](base: ScopeAxis[T]): Seq[ScopeAxis[T]] =
    if (base.isSelect) List(base, Zero: ScopeAxis[T])
    else zeroL

  def withGlobalScope(base: Scope): Seq[Scope] =
    if (base == GlobalScope) GlobalScope :: Nil else base :: GlobalScope :: Nil
  def withRawBuilds(ps: Seq[ScopeAxis[ProjectRef]]): Seq[ScopeAxis[ResolvedReference]] =
    (ps: Seq[ScopeAxis[ResolvedReference]]) ++
      ((ps flatMap rawBuild).distinct: Seq[ScopeAxis[ResolvedReference]]) :+
      (Zero: ScopeAxis[ResolvedReference])

  def rawBuild(ps: ScopeAxis[ProjectRef]): Seq[ScopeAxis[BuildRef]] = ps match {
    case Select(ref) => Select(BuildRef(ref.build)) :: Nil; case _ => Nil
  }

  def delegates[Proj](
      refs: Seq[(ProjectRef, Proj)],
      configurations: Proj => Seq[ConfigKey],
      projectInherit: ProjectRef => Seq[ProjectRef],
      configInherit: (ResolvedReference, ConfigKey) => Seq[ConfigKey]
  ): DelegateIndex = {
    val pDelegates = refs map {
      case (ref, project) =>
        (ref, delegateIndex(ref, configurations(project))(projectInherit, configInherit))
    } toMap;
    new DelegateIndex0(pDelegates)
  }
  private[this] def delegateIndex(ref: ProjectRef, confs: Seq[ConfigKey])(
      projectInherit: ProjectRef => Seq[ProjectRef],
      configInherit: (ResolvedReference, ConfigKey) => Seq[ConfigKey]
  ): ProjectDelegates = {
    val refDelegates = withRawBuilds(linearize(Select(ref), false)(projectInherit))
    val configs = confs map { c =>
      axisDelegates(configInherit, ref, c)
    }
    new ProjectDelegates(ref, refDelegates, configs.toMap)
  }
  def axisDelegates[T](
      direct: (ResolvedReference, T) => Seq[T],
      ref: ResolvedReference,
      init: T
  ): (T, Seq[ScopeAxis[T]]) =
    (init, linearize(Select(init))(direct(ref, _)))

  def linearize[T](axis: ScopeAxis[T], appendZero: Boolean = true)(
      inherit: T => Seq[T]
  ): Seq[ScopeAxis[T]] =
    axis match {
      case Select(x)   => topologicalSort[T](x, appendZero)(inherit)
      case Zero | This => if (appendZero) Zero :: Nil else Nil
    }

  def topologicalSort[T](node: T, appendZero: Boolean)(
      dependencies: T => Seq[T]
  ): Seq[ScopeAxis[T]] = {
    val o = Dag.topologicalSortUnchecked(node)(dependencies).map(x => Select(x): ScopeAxis[T])
    if (appendZero) o ::: (Zero: ScopeAxis[T]) :: Nil
    else o
  }
  def globalProjectDelegates(scope: Scope): Seq[Scope] =
    if (scope == GlobalScope)
      GlobalScope :: Nil
    else
      for {
        c <- withZeroAxis(scope.config)
        t <- withZeroAxis(scope.task)
        e <- withZeroAxis(scope.extra)
      } yield Scope(Zero, c, t, e)
}
