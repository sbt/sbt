/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util.{ AttributeKey, Dag, Relation, Util }
import sbt.util.Logger

import Def.Setting
import Plugins._
import PluginsDebug._
import java.net.URI

private[sbt] class PluginsDebug(
    val available: List[AutoPlugin],
    val nameToKey: Map[String, AttributeKey[_]],
    val provided: Relation[AutoPlugin, AttributeKey[_]]
) {

  /**
   * The set of [[AutoPlugin]]s that might define a key named `keyName`.
   * Because plugins can define keys in different scopes, this should only be used as a guideline.
   */
  def providers(keyName: String): Set[AutoPlugin] = nameToKey.get(keyName) match {
    case None      => Set.empty
    case Some(key) => provided.reverse(key)
  }

  /** Describes alternative approaches for defining key `keyName` in [[Context]]. */
  def toEnable(keyName: String, context: Context): List[PluginEnable] =
    providers(keyName).toList.map(plugin => pluginEnable(context, plugin))

  /** Provides text to suggest how `notFoundKey` can be defined in [[Context]]. */
  def debug(notFoundKey: String, context: Context): String = {
    val (activated, deactivated) = Util.separate(toEnable(notFoundKey, context)) {
      case pa: PluginActivated   => Left(pa)
      case pd: EnableDeactivated => Right(pd)
    }
    val activePrefix =
      if (activated.isEmpty) ""
      else
        s"Some already activated plugins define $notFoundKey: ${activated.mkString(", ")}\n"
    activePrefix + debugDeactivated(notFoundKey, deactivated)
  }

  private[this] def debugDeactivated(notFoundKey: String,
                                     deactivated: Seq[EnableDeactivated]): String = {
    val (impossible, possible) = Util.separate(deactivated) {
      case pi: PluginImpossible   => Left(pi)
      case pr: PluginRequirements => Right(pr)
    }
    if (possible.nonEmpty) {
      val explained = possible.map(explainPluginEnable)
      val possibleString =
        if (explained.size > 1)
          explained.zipWithIndex
            .map { case (s, i) => s"$i. $s" }
            .mkString(s"Multiple plugins are available that can provide $notFoundKey:\n", "\n", "")
        else
          s"$notFoundKey is provided by an available (but not activated) plugin:\n${explained.mkString}"
      def impossiblePlugins = impossible.map(_.plugin.label).mkString(", ")
      val imPostfix =
        if (impossible.isEmpty) ""
        else
          s"\n\nThere are other available plugins that provide $notFoundKey, but they are " +
            s"impossible to add: $impossiblePlugins"
      possibleString + imPostfix
    } else if (impossible.isEmpty)
      s"No available plugin provides key $notFoundKey."
    else {
      val explanations = impossible.map(explainPluginEnable)
      val preamble = s"Plugins are available that could provide $notFoundKey"
      explanations.mkString(s"$preamble, but they are impossible to add:\n\t", "\n\t", "")
    }
  }

  /** Text that suggests how to activate [[AutoPlugin]] in [[Context]] if possible and if it is not already activated. */
  def help(plugin: AutoPlugin, context: Context): String =
    if (context.enabled.contains(plugin)) activatedHelp(plugin)
    else deactivatedHelp(plugin, context)

  private def activatedHelp(plugin: AutoPlugin): String = {
    val prefix = s"${plugin.label} is activated."
    val keys = provided.forward(plugin)
    val keysString =
      if (keys.isEmpty) "" else s"\nIt may affect these keys: ${multi(keys.toList.map(_.label))}"
    val configs = plugin.projectConfigurations
    val confsString =
      if (configs.isEmpty) ""
      else s"\nIt defines these configurations: ${multi(configs.map(_.name))}"
    prefix + keysString + confsString
  }

  private def deactivatedHelp(plugin: AutoPlugin, context: Context): String = {
    val prefix = s"${plugin.label} is NOT activated."
    val keys = provided.forward(plugin)
    val keysString =
      if (keys.isEmpty) ""
      else s"\nActivating it may affect these keys: ${multi(keys.toList.map(_.label))}"
    val configs = plugin.projectConfigurations
    val confsString =
      if (configs.isEmpty) ""
      else s"\nActivating it will define these configurations: ${multi(configs.map(_.name))}"
    val toActivate = explainPluginEnable(pluginEnable(context, plugin))
    s"$prefix$keysString$confsString\n$toActivate"
  }

  private[this] def multi(strs: Seq[String]): String =
    strs.mkString(if (strs.size > 4) "\n\t" else ", ")
}

private[sbt] object PluginsDebug {
  def helpAll(s: State): String =
    if (Project.isProjectLoaded(s)) {
      val extracted = Project.extract(s)
      import extracted._
      def helpBuild(uri: URI, build: LoadedBuildUnit): String = {
        val pluginStrings = for (plugin <- availableAutoPlugins(build)) yield {
          val activatedIn =
            build.defined.values.toList.filter(_.autoPlugins.contains(plugin)).map(_.id)
          val actString =
            if (activatedIn.nonEmpty) activatedIn.mkString(": enabled in ", ", ", "")
            else "" // TODO: deal with large builds
          s"\n\t${plugin.label}$actString"
        }
        s"In $uri${pluginStrings.mkString}"
      }
      val buildStrings = for ((uri, build) <- structure.units) yield helpBuild(uri, build)
      buildStrings.mkString("\n")
    } else "No project is currently loaded."

  def autoPluginMap(s: State): Map[String, AutoPlugin] = {
    val extracted = Project.extract(s)
    import extracted._
    structure.units.values.toList
      .flatMap(availableAutoPlugins)
      .map(plugin => (plugin.label, plugin))
      .toMap
  }

  private[this] def availableAutoPlugins(build: LoadedBuildUnit): Seq[AutoPlugin] =
    build.unit.plugins.detected.autoPlugins map { _.value }

  def help(plugin: AutoPlugin, s: State): String = {
    val extracted = Project.extract(s)
    import extracted._
    def definesPlugin(p: ResolvedProject): Boolean = p.autoPlugins.contains(plugin)
    def projectForRef(ref: ProjectRef): ResolvedProject = get(Keys.thisProject in ref)
    val perBuild: Map[URI, Set[AutoPlugin]] =
      structure.units.mapValues(unit => availableAutoPlugins(unit).toSet)
    val pluginsThisBuild = perBuild.getOrElse(currentRef.build, Set.empty).toList
    lazy val context = Context(currentProject.plugins,
                               currentProject.autoPlugins,
                               Plugins.deducer(pluginsThisBuild),
                               pluginsThisBuild,
                               s.log)
    lazy val debug = PluginsDebug(context.available)
    if (!pluginsThisBuild.contains(plugin)) {
      val availableInBuilds: List[URI] = perBuild.toList.filter(_._2(plugin)).map(_._1)
      val s1 = s"Plugin ${plugin.label} is only available in builds:"
      val s2 = availableInBuilds.mkString("\n\t")
      val s3 =
        s"Switch to a project in one of those builds using `project` and rerun this command for more information."
      s"$s1\n\t$s2\n$s3"
    } else if (definesPlugin(currentProject))
      debug.activatedHelp(plugin)
    else {
      val thisAggregated =
        BuildUtil.dependencies(structure.units).aggregateTransitive.getOrElse(currentRef, Nil)
      val definedInAggregated = thisAggregated.filter(ref => definesPlugin(projectForRef(ref)))
      if (definedInAggregated.nonEmpty) {
        val projectNames = definedInAggregated.map(_.project) // TODO: usually in this build, but could technically require the build to be qualified
        val s2 = projectNames.mkString("\n\t")
        s"Plugin ${plugin.label} is not activated on this project, but this project aggregates projects where it is activated:\n\t$s2"
      } else {
        val base = debug.deactivatedHelp(plugin, context)
        val aggNote =
          if (thisAggregated.nonEmpty) "Note: This project aggregates other projects and this"
          else "Note: This"
        val common = " information is for this project only."
        val helpOther =
          "To see how to activate this plugin for another project, change to the project using `project <name>` and rerun this command."
        s"$base\n$aggNote$common\n$helpOther"
      }
    }
  }

  /** Pre-computes information for debugging plugins. */
  def apply(available: List[AutoPlugin]): PluginsDebug = {
    val keyR = definedKeys(available)
    val nameToKey: Map[String, AttributeKey[_]] =
      keyR._2s.toList.map(key => (key.label, key)).toMap
    new PluginsDebug(available, nameToKey, keyR)
  }

  /**
   * The context for debugging a plugin (de)activation.
   * @param initial The initially defined [[AutoPlugin]]s.
   * @param enabled The resulting model.
   * @param deducePlugin The function used to compute the model.
   * @param available All [[AutoPlugin]]s available for consideration.
   */
  final case class Context(
      initial: Plugins,
      enabled: Seq[AutoPlugin],
      deducePlugin: (Plugins, Logger) => Seq[AutoPlugin],
      available: List[AutoPlugin],
      log: Logger
  )

  /** Describes the steps to activate a plugin in some context. */
  sealed abstract class PluginEnable

  /** Describes a [[plugin]] that is already activated in the [[context]].*/
  final case class PluginActivated(plugin: AutoPlugin, context: Context) extends PluginEnable

  sealed abstract class EnableDeactivated extends PluginEnable

  /** Describes a [[plugin]] that cannot be activated in a [[context]] due to [[contradictions]] in requirements. */
  final case class PluginImpossible(plugin: AutoPlugin,
                                    context: Context,
                                    contradictions: Set[AutoPlugin])
      extends EnableDeactivated

  /**
   * Describes the requirements for activating [[plugin]] in [[context]].
   * @param context The base plugins, exclusions, and ultimately activated plugins
   * @param blockingExcludes Existing exclusions that prevent [[plugin]] from being activated and must be dropped
   * @param enablingPlugins [[AutoPlugin]]s that are not currently enabled,
   *                        but need to be enabled for [[plugin]] to activate
   * @param extraEnabledPlugins Plugins that will be enabled as a result of [[plugin]] activating,
   *                            but are not required for [[plugin]] to activate
   * @param willRemove Plugins that will be deactivated as a result of [[plugin]] activating
   * @param deactivate Describes plugins that must be deactivated for [[plugin]] to activate.
   *                   These require an explicit exclusion or dropping a transitive [[AutoPlugin]].
   */
  final case class PluginRequirements(
      plugin: AutoPlugin,
      context: Context,
      blockingExcludes: Set[AutoPlugin],
      enablingPlugins: Set[AutoPlugin],
      extraEnabledPlugins: Set[AutoPlugin],
      willRemove: Set[AutoPlugin],
      deactivate: List[DeactivatePlugin]
  ) extends EnableDeactivated

  /**
   * Describes a [[plugin]] that must be removed in order to activate another plugin in some context.
   * The [[plugin]] can always be directly, explicitly excluded.
   * @param removeOneOf If non-empty, removing one of these [[AutoPlugin]]s will deactivate [[plugin]] without
   *                    affecting the other plugin.  If empty, a direct exclusion is required.
   * @param newlySelected If false, this plugin was selected in the original context.
   */
  final case class DeactivatePlugin(plugin: AutoPlugin,
                                    removeOneOf: Set[AutoPlugin],
                                    newlySelected: Boolean)

  /** Determines how to enable [[AutoPlugin]] in [[Context]]. */
  def pluginEnable(context: Context, plugin: AutoPlugin): PluginEnable =
    if (context.enabled.contains(plugin))
      PluginActivated(plugin, context)
    else
      enableDeactivated(context, plugin)

  private[this] def enableDeactivated(context: Context, plugin: AutoPlugin): PluginEnable = {
    // deconstruct the context
    val initialModel = context.enabled.toSet
    val initial = flatten(context.initial)
    val initialPlugins = plugins(initial)
    val initialExcludes = excludes(initial)

    val minModel = minimalModel(plugin)

    /* example 1
    A :- B, not C
    C :- D, E
    initial: B, D, E
    propose: drop D or E

    initial: B, not A
    propose: drop 'not A'

    example 2
    A :- B, not C
    C :- B
    initial: <empty>
    propose: B, exclude C
     */

    // `plugin` will only be activated when all of these plugins are activated
    // Deactivating any one of these would deactivate `plugin`.
    val minRequiredPlugins = plugins(minModel)

    // The presence of any one of these plugins would deactivate `plugin`
    val minAbsentPlugins = excludes(minModel)

    // Plugins that must be both activated and deactivated for `plugin` to activate.
    //  A non-empty list here cannot be satisfied and is an error.
    val contradictions = minAbsentPlugins & minRequiredPlugins

    if (contradictions.nonEmpty) PluginImpossible(plugin, context, contradictions)
    else {
      // Plugins that the user has to add to the currently selected plugins in order to enable `plugin`.
      val addToExistingPlugins = minRequiredPlugins -- initialPlugins

      // Plugins that are currently excluded that need to be allowed.
      val blockingExcludes = initialExcludes & minRequiredPlugins

      // The model that results when the minimal plugins are enabled and the minimal plugins are excluded.
      //  This can include more plugins than just `minRequiredPlugins` because the plugins required for `plugin`
      //  might activate other plugins as well.
      val incrementalInputs = and(
        includeAll(minRequiredPlugins ++ initialPlugins),
        excludeAll(minAbsentPlugins ++ initialExcludes -- minRequiredPlugins)
      )
      val incrementalModel = context.deducePlugin(incrementalInputs, context.log).toSet

      // Plugins that are newly enabled as a result of selecting the plugins needed for `plugin`, but aren't strictly required for `plugin`.
      //   These could be excluded and `plugin` and the user's current plugins would still be activated.
      val extraPlugins = incrementalModel -- minRequiredPlugins -- initialModel

      // Plugins that will no longer be enabled as a result of enabling `plugin`.
      val willRemove = initialModel -- incrementalModel

      // Determine the plugins that must be independently deactivated.
      // If both A and B must be deactivated, but A transitively depends on B, deactivating B will deactivate A.
      // If A must be deactivated, but one if its (transitively) required plugins isn't present, it won't be activated.
      //   So, in either of these cases, A doesn't need to be considered further and won't be included in this set.
      val minDeactivate =
        minAbsentPlugins.filter(p => Plugins.satisfied(p.requires, incrementalModel))

      val deactivate = for (d <- minDeactivate.toList) yield {
        // removing any one of these plugins will deactivate `d`.  TODO: This is not an especially efficient implementation.
        val removeToDeactivate = plugins(minimalModel(d)) -- minRequiredPlugins
        val newlySelected = !initialModel(d)
        // a. suggest removing a plugin in removeOneToDeactivate to deactivate d
        // b. suggest excluding `d` to directly deactivate it in any case
        // c. note whether d was already activated (in context.enabled) or is newly selected
        DeactivatePlugin(d, removeToDeactivate, newlySelected)
      }

      PluginRequirements(plugin,
                         context,
                         blockingExcludes,
                         addToExistingPlugins,
                         extraPlugins,
                         willRemove,
                         deactivate)
    }
  }

  private[this] def includeAll[T <: Basic](basic: Set[T]): Plugins = And(basic.toList)
  private[this] def excludeAll(plugins: Set[AutoPlugin]): Plugins =
    And(plugins map (p => Exclude(p)) toList)

  private[this] def excludes(bs: Seq[Basic]): Set[AutoPlugin] =
    bs.collect { case Exclude(b) => b }.toSet
  private[this] def plugins(bs: Seq[Basic]): Set[AutoPlugin] =
    bs.collect { case n: AutoPlugin => n }.toSet

  // If there is a model that includes `plugin`, it includes at least what is returned by this method.
  // This is the list of plugins that must be included as well as list of plugins that must not be present.
  // It might not be valid, such as if there are contradictions or if there are cycles that are unsatisfiable.
  // The actual model might be larger, since other plugins might be enabled by the selected plugins.
  private[this] def minimalModel(plugin: AutoPlugin): Seq[Basic] =
    Dag.topologicalSortUnchecked(plugin: Basic) {
      case _: Exclude     => Nil
      case ap: AutoPlugin => Plugins.flatten(ap.requires) :+ plugin
    }

  /** String representation of [[PluginEnable]], intended for end users. */
  def explainPluginEnable(ps: PluginEnable): String =
    ps match {
      case PluginRequirements(plugin,
                              context,
                              blockingExcludes,
                              enablingPlugins,
                              extraEnabledPlugins,
                              toBeRemoved,
                              deactivate) =>
        def indent(str: String) = if (str.isEmpty) "" else s"\t$str"
        def note(str: String) = if (str.isEmpty) "" else s"Note: $str"
        val parts =
          indent(excludedError(false /* TODO */, blockingExcludes.toList)) ::
            indent(required(enablingPlugins.toList)) ::
            indent(needToDeactivate(deactivate)) ::
            note(willAdd(plugin, extraEnabledPlugins.toList)) ::
            note(willRemove(plugin, toBeRemoved.toList)) ::
            Nil
        parts.filterNot(_.isEmpty).mkString("\n")
      case PluginImpossible(plugin, context, contradictions) =>
        pluginImpossible(plugin, contradictions)
      case PluginActivated(plugin, context) => s"Plugin ${plugin.label} already activated."
    }

  /**
   * Provides a [[Relation]] between plugins and the keys they potentially define.
   * Because plugins can define keys in different scopes and keys can be overridden, this is not definitive.
   */
  def definedKeys(available: List[AutoPlugin]): Relation[AutoPlugin, AttributeKey[_]] = {
    def extractDefinedKeys(ss: Seq[Setting[_]]): Seq[AttributeKey[_]] =
      ss.map(_.key.key)
    def allSettings(p: AutoPlugin): Seq[Setting[_]] =
      p.projectSettings ++ p.buildSettings ++ p.globalSettings
    val empty = Relation.empty[AutoPlugin, AttributeKey[_]]
    (empty /: available)((r, p) => r + (p, extractDefinedKeys(allSettings(p))))
  }

  private[this] def excludedError(transitive: Boolean, dependencies: List[AutoPlugin]): String =
    str(dependencies)(excludedPluginError(transitive), excludedPluginsError(transitive))

  private[this] def excludedPluginError(transitive: Boolean)(dependency: AutoPlugin) =
    s"Required ${transitiveString(transitive)}dependency ${dependency.label} was excluded."

  private[this] def excludedPluginsError(transitive: Boolean)(dependencies: List[AutoPlugin]) =
    s"Required ${transitiveString(transitive)}dependencies were excluded:\n\t${labels(dependencies)
      .mkString("\n\t")}"

  private[this] def transitiveString(transitive: Boolean) =
    if (transitive) "(transitive) " else ""

  private[this] def required(plugins: List[AutoPlugin]): String =
    str(plugins)(requiredPlugin, requiredPlugins)

  private[this] def requiredPlugin(plugin: AutoPlugin) =
    s"Required plugin ${plugin.label} not present."

  private[this] def requiredPlugins(plugins: List[AutoPlugin]) =
    s"Required plugins not present:\n\t${plugins.map(_.label).mkString("\n\t")}"

  private[this] def str[A](list: List[A])(f: A => String, fs: List[A] => String): String =
    list match {
      case Nil           => ""
      case single :: Nil => f(single)
      case _             => fs(list)
    }

  private[this] def willAdd(base: AutoPlugin, plugins: List[AutoPlugin]): String =
    str(plugins)(willAddPlugin(base), willAddPlugins(base))

  private[this] def willAddPlugin(base: AutoPlugin)(plugin: AutoPlugin) =
    s"Enabling ${base.label} will also enable ${plugin.label}"

  private[this] def willAddPlugins(base: AutoPlugin)(plugins: List[AutoPlugin]) =
    s"Enabling ${base.label} will also enable:\n\t${labels(plugins).mkString("\n\t")}"

  private[this] def willRemove(base: AutoPlugin, plugins: List[AutoPlugin]): String =
    str(plugins)(willRemovePlugin(base), willRemovePlugins(base))

  private[this] def willRemovePlugin(base: AutoPlugin)(plugin: AutoPlugin) =
    s"Enabling ${base.label} will disable ${plugin.label}"

  private[this] def willRemovePlugins(base: AutoPlugin)(plugins: List[AutoPlugin]) =
    s"Enabling ${base.label} will disable:\n\t${labels(plugins).mkString("\n\t")}"

  private[this] def labels(plugins: List[AutoPlugin]): List[String] =
    plugins.map(_.label)

  private[this] def needToDeactivate(deactivate: List[DeactivatePlugin]): String =
    str(deactivate)(deactivate1, deactivateN)

  private[this] def deactivateN(plugins: List[DeactivatePlugin]): String =
    plugins.map(deactivateString).mkString("These plugins need to be deactivated:\n\t", "\n\t", "")

  private[this] def deactivate1(deactivate: DeactivatePlugin): String =
    s"Need to deactivate ${deactivateString(deactivate)}"

  private[this] def deactivateString(d: DeactivatePlugin): String = {
    val removePluginsString: String =
      d.removeOneOf.toList match {
        case Nil      => ""
        case x :: Nil => s" or no longer include $x"
        case xs       => s" or remove one of ${xs.mkString(", ")}"
      }
    s"${d.plugin.label}: directly exclude it${removePluginsString}"
  }

  private[this] def pluginImpossible(plugin: AutoPlugin, contradictions: Set[AutoPlugin]): String =
    str(contradictions.toList)(pluginImpossible1(plugin), pluginImpossibleN(plugin))

  private[this] def pluginImpossible1(plugin: AutoPlugin)(contradiction: AutoPlugin): String = {
    val s1 = s"There is no way to enable plugin ${plugin.label}."
    val s2 =
      s"It (or its dependencies) requires plugin ${contradiction.label} to both be present and absent."
    val s3 = s"Please report the problem to the plugin's author."
    s"$s1  $s2  $s3"
  }

  private[this] def pluginImpossibleN(plugin: AutoPlugin)(
      contradictions: List[AutoPlugin]): String = {
    val s1 = s"There is no way to enable plugin ${plugin.label}."
    val s2 = s"It (or its dependencies) requires these plugins to be both present and absent:"
    val s3 = s"Please report the problem to the plugin's author."
    s"$s1  $s2:\n\t${labels(contradictions).mkString("\n\t")}\n$s3"
  }
}
