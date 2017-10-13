/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
/*
TODO:
- index all available AutoPlugins to get the tasks that will be added
- error message when a task doesn't exist that it would be provided by plugin x, enabled by natures y,z, blocked by a, b
 */

import sbt.librarymanagement.Configuration

import sbt.internal.util.logic.{ Atom, Clause, Clauses, Formula, Literal, Logic, Negated }
import Logic.{ CyclicNegation, InitialContradictions, InitialOverlap, LogicException }
import Def.Setting
import Plugins._
import annotation.tailrec
import sbt.util.Logger
import PluginTrigger._

/**
 * An AutoPlugin defines a group of settings and the conditions where the settings are automatically added to a build (called "activation").
 * The `requires` and `trigger` methods together define the conditions, and a method like `projectSettings` defines the settings to add.
 *
 * Steps for plugin authors:
 *
 *   1. Determine if the `AutoPlugin` should automatically be activated when all requirements are met, or should be opt-in.
 *   1. Determine the `AutoPlugin`s that, when present (or absent), act as the requirements for the `AutoPlugin`.
 *   1. Determine the settings/configurations to that the `AutoPlugin` injects when activated.
 *   1. Determine the keys and other names to be automatically imported to `*.sbt` scripts.
 *
 * For example, the following will automatically add the settings in `projectSettings`
 * to a project that has both the `Web` and `Javascript` plugins enabled.
 *
 * {{{
 * object MyPlugin extends sbt.AutoPlugin {
 *   override def requires = Web && Javascript
 *   override def trigger = allRequirements
 *   override def projectSettings = Seq(...)
 *
 *   object autoImport {
 *     lazy val obfuscate = taskKey[Seq[File]]("Obfuscates the source.")
 *   }
 * }
 * }}}
 *
 * Steps for users:
 *
 *   1. Add dependencies on plugins in `project/plugins.sbt` as usual with `addSbtPlugin`
 *   1. Add key plugins to projects, which will automatically select the plugin + dependent plugin settings to add for those projects.
 *   1. Exclude plugins, if desired.
 *
 * For example, given plugins Web and Javascript (perhaps provided by plugins added with `addSbtPlugin`),
 *
 * {{{
 * myProject.enablePlugins(Web && Javascript)
 * }}}
 *
 * will activate `MyPlugin` defined above and have its settings automatically added.  If the user instead defines
 * {{{
 * myProject.enablePlugins(Web && Javascript).disablePlugins(MyPlugin)
 * }}}
 *
 * then the `MyPlugin` settings (and anything that activates only when `MyPlugin` is activated) will not be added.
 */
abstract class AutoPlugin extends Plugins.Basic with PluginsFunctions {

  /**
   * Determines whether this AutoPlugin will be activated for this project when the `requires` clause is satisfied.
   *
   * When this method returns `allRequirements`, and `requires` method returns `Web && Javascript`, this plugin
   * instance will be added automatically if the `Web` and `Javascript` plugins are enabled.
   *
   * When this method returns `noTrigger`, and `requires` method returns `Web && Javascript`, this plugin
   * instance will be added only if the build user enables it, but it will automatically add both `Web` and `Javascript`.
   */
  def trigger: PluginTrigger = noTrigger

  /**
   * This AutoPlugin requires the plugins the [[Plugins]] matcher returned by this method. See [[trigger]].
   */
  def requires: Plugins = plugins.JvmPlugin

  val label: String = getClass.getName.stripSuffix("$")

  override def toString: String = label

  /** The [[Configuration]]s to add to each project that activates this AutoPlugin.*/
  def projectConfigurations: Seq[Configuration] = Nil

  /** The [[Setting]]s to add in the scope of each project that activates this AutoPlugin. */
  def projectSettings: Seq[Setting[_]] = Nil

  /**
   * The [[Setting]]s to add to the build scope for each project that activates this AutoPlugin.
   * The settings returned here are guaranteed to be added to a given build scope only once
   * regardless of how many projects for that build activate this AutoPlugin.
   */
  def buildSettings: Seq[Setting[_]] = Nil

  /** The [[Setting]]s to add to the global scope exactly once if any project activates this AutoPlugin. */
  def globalSettings: Seq[Setting[_]] = Nil

  // TODO?: def commands: Seq[Command]

  /** The [[Project]]s to add to the current build. */
  def extraProjects: Seq[Project] = Nil

  /** The [[Project]]s to add to the current build based on an existing project. */
  def derivedProjects(proj: ProjectDefinition[_]): Seq[Project] = Nil

  private[sbt] def unary_! : Exclude = Exclude(this)

  /** If this plugin does not have any requirements, it means it is actually a root plugin. */
  private[sbt] final def isRoot: Boolean =
    requires match {
      case Empty => true
      case _     => false
    }

  /** If this plugin does not have any requirements, it means it is actually a root plugin. */
  private[sbt] final def isAlwaysEnabled: Boolean =
    isRoot && (trigger == AllRequirements)
}

/**
 * An error that occurs when auto-plugins aren't configured properly.
 * It translates the error from the underlying logic system to be targeted at end users.
 */
final class AutoPluginException private (val message: String, val origin: Option[LogicException])
    extends RuntimeException(message) {

  /** Prepends `p` to the error message derived from `origin`. */
  def withPrefix(p: String) = new AutoPluginException(p + message, origin)
}
object AutoPluginException {
  def apply(msg: String): AutoPluginException = new AutoPluginException(msg, None)
  def apply(origin: LogicException): AutoPluginException =
    new AutoPluginException(Plugins.translateMessage(origin), Some(origin))
}

/** An expression that matches `AutoPlugin`s. */
sealed trait Plugins {
  def &&(o: Basic): Plugins
}

sealed trait PluginsFunctions {

  /** [[Plugins]] instance that doesn't require any [[Plugins]]s. */
  def empty: Plugins = Plugins.Empty

  /** This plugin is activated when all required plugins are present. */
  def allRequirements: PluginTrigger = AllRequirements

  /** This plugin is activated only when it is manually activated. */
  def noTrigger: PluginTrigger = NoTrigger
}

object Plugins extends PluginsFunctions {

  /**
   * Given the available auto plugins `defined`, returns a function that selects [[AutoPlugin]]s for the provided [[AutoPlugin]]s.
   * The [[AutoPlugin]]s are topologically sorted so that a required [[AutoPlugin]] comes before its requiring [[AutoPlugin]].
   */
  def deducer(defined0: List[AutoPlugin]): (Plugins, Logger) => Seq[AutoPlugin] =
    if (defined0.isEmpty)(_, _) => Nil
    else {
      // TODO: defined should return all the plugins
      val allReqs = (defined0 flatMap { asRequirements }).toSet
      val diff = allReqs diff defined0.toSet
      val defined =
        if (diff.nonEmpty) diff.toList ::: defined0
        else defined0

      val byAtom = defined map { x =>
        (Atom(x.label), x)
      }
      val byAtomMap = byAtom.toMap
      if (byAtom.size != byAtomMap.size) duplicateProvidesError(byAtom)
      // Ignore clauses for plugins that does not require anything else.
      // Avoids the requirement for pure Nature strings *and* possible
      // circular dependencies in the logic.
      val allRequirementsClause =
        defined.filterNot(_.isRoot).flatMap(d => asRequirementsClauses(d))
      val allEnabledByClause = defined.filterNot(_.isRoot).flatMap(d => asEnabledByClauses(d))

      // Note: Here is where the function begins.  We're given a list of plugins now.
      (requestedPlugins, log) =>
        {
          timed("Plugins.deducer#function", log) {
            def explicitlyDisabled(p: AutoPlugin): Boolean = hasExclude(requestedPlugins, p)
            val alwaysEnabled: List[AutoPlugin] =
              defined.filter(_.isAlwaysEnabled).filterNot(explicitlyDisabled)
            val knowledge0: Set[Atom] = ((flatten(requestedPlugins) ++ alwaysEnabled) collect {
              case x: AutoPlugin => Atom(x.label)
            }).toSet
            val clauses = Clauses((allRequirementsClause ::: allEnabledByClause) filterNot {
              _.head subsetOf knowledge0
            })
            log.debug(
              s"deducing auto plugins based on known facts ${knowledge0.toString} and clauses ${clauses.toString}")
            Logic.reduce(
              clauses,
              (flattenConvert(requestedPlugins) ++ convertAll(alwaysEnabled)).toSet) match {
              case Left(problem) => throw AutoPluginException(problem)
              case Right(results) =>
                log.debug(s"  :: deduced result: ${results}")
                val selectedAtoms: List[Atom] = results.ordered
                val selectedPlugins = selectedAtoms map { a =>
                  byAtomMap.getOrElse(
                    a,
                    throw AutoPluginException(s"${a} was not found in atom map.")
                  )
                }
                val forbidden: Set[AutoPlugin] =
                  (selectedPlugins flatMap { Plugins.asExclusions }).toSet
                val c = selectedPlugins.toSet & forbidden
                if (c.nonEmpty) {
                  exclusionConflictError(requestedPlugins, selectedPlugins, c.toSeq sortBy {
                    _.label
                  })
                }
                val retval = topologicalSort(selectedPlugins, log)
                // log.debug(s"  :: sorted deduced result: ${retval.toString}")
                retval
            }
          }
        }
    }
  private[sbt] def topologicalSort(ns: List[AutoPlugin], log: Logger): List[AutoPlugin] = {
    // log.debug(s"sorting: ns: ${ns.toString}")
    @tailrec
    def doSort(found0: List[AutoPlugin],
               notFound0: List[AutoPlugin],
               limit0: Int): List[AutoPlugin] = {
      // log.debug(s"  :: sorting:: found: ${found0.toString} not found ${notFound0.toString}")
      if (limit0 < 0) throw AutoPluginException(s"Failed to sort ${ns} topologically")
      else if (notFound0.isEmpty) found0
      else {
        val (found1, notFound1) = notFound0 partition { n =>
          asRequirements(n).toSet subsetOf found0.toSet
        }
        doSort(found0 ::: found1, notFound1, limit0 - 1)
      }
    }
    val (roots, nonRoots) = ns partition (_.isRoot)
    doSort(roots, nonRoots, ns.size * ns.size + 1)
  }
  private[sbt] def translateMessage(e: LogicException) = e match {
    case ic: InitialContradictions =>
      s"Contradiction in selected plugins.  These plugins were both included and excluded: ${literalsString(
        ic.literals.toSeq)}"
    case io: InitialOverlap =>
      s"Cannot directly enable plugins.  Plugins are enabled when their required plugins are satisfied.  The directly selected plugins were: ${literalsString(
        io.literals.toSeq)}"
    case cn: CyclicNegation =>
      s"Cycles in plugin requirements cannot involve excludes.  The problematic cycle is: ${literalsString(cn.cycle)}"
  }
  private[this] def literalsString(lits: Seq[Literal]): String =
    lits map { case Atom(l) => l; case Negated(Atom(l)) => l } mkString (", ")

  private[this] def duplicateProvidesError(byAtom: Seq[(Atom, AutoPlugin)]): Unit = {
    val dupsByAtom = byAtom.groupBy(_._1).mapValues(_.map(_._2))
    val dupStrings = for ((atom, dups) <- dupsByAtom if dups.size > 1)
      yield s"${atom.label} by ${dups.mkString(", ")}"
    val (ns, nl) = if (dupStrings.size > 1) ("s", "\n\t") else ("", " ")
    val message = s"Plugin$ns provided by multiple AutoPlugins:$nl${dupStrings.mkString(nl)}"
    throw AutoPluginException(message)
  }
  private[this] def exclusionConflictError(requested: Plugins,
                                           selected: Seq[AutoPlugin],
                                           conflicting: Seq[AutoPlugin]): Unit = {
    def listConflicts(ns: Seq[AutoPlugin]) =
      (ns map { c =>
        val reasons = (if (flatten(requested) contains c) List("requested")
                       else Nil) ++
          (if (c.requires != empty && c.trigger == allRequirements)
             List(s"enabled by ${c.requires.toString}")
           else Nil) ++ {
          val reqs = selected filter { x =>
            asRequirements(x) contains c
          }
          if (reqs.nonEmpty) List(s"""required by ${reqs.mkString(", ")}""")
          else Nil
        } ++ {
          val exs = selected filter { x =>
            asExclusions(x) contains c
          }
          if (exs.nonEmpty) List(s"""excluded by ${exs.mkString(", ")}""")
          else Nil
        }
        s"""  - conflict: ${c.label} is ${reasons.mkString("; ")}"""
      }).mkString("\n")
    throw AutoPluginException(s"""Contradiction in enabled plugins:
  - requested: ${requested.toString}
  - enabled: ${selected.mkString(", ")}
${listConflicts(conflicting)}""")
  }

  private[sbt] final object Empty extends Plugins {
    def &&(o: Basic): Plugins = o
    override def toString = "<none>"
  }

  /** An included or excluded Nature/Plugin. */
  // TODO: better name than Basic.  Also, can we dump this class
  sealed abstract class Basic extends Plugins {
    def &&(o: Basic): Plugins = And(this :: o :: Nil)
  }
  private[sbt] final case class Exclude(n: AutoPlugin) extends Basic {
    override def toString = s"!$n"
  }
  private[sbt] final case class And(plugins: List[Basic]) extends Plugins {
    def &&(o: Basic): Plugins = And(o :: plugins)
    override def toString = plugins.mkString(" && ")
  }
  private[sbt] def and(a: Plugins, b: Plugins) = b match {
    case Empty    => a
    case And(ns)  => (a /: ns)(_ && _)
    case b: Basic => a && b
  }
  private[sbt] def remove(a: Plugins, del: Set[Basic]): Plugins = a match {
    case b: Basic => if (del(b)) Empty else b
    case Empty    => Empty
    case And(ns) =>
      val removed = ns.filterNot(del)
      if (removed.isEmpty) Empty else And(removed)
  }

  /** Defines enabled-by clauses for `ap`. */
  private[sbt] def asEnabledByClauses(ap: AutoPlugin): List[Clause] =
    // `ap` is the head and the required plugins for `ap` is the body.
    if (ap.trigger == AllRequirements) Clause(convert(ap.requires), Set(Atom(ap.label))) :: Nil
    else Nil

  /** Defines requirements clauses for `ap`. */
  private[sbt] def asRequirementsClauses(ap: AutoPlugin): List[Clause] =
    // required plugin is the head and `ap` is the body.
    asRequirements(ap) map { x =>
      Clause(convert(ap), Set(Atom(x.label)))
    }
  private[sbt] def asRequirements(ap: AutoPlugin): List[AutoPlugin] =
    flatten(ap.requires).toList collect {
      case x: AutoPlugin => x
    }
  private[sbt] def asExclusions(ap: AutoPlugin): List[AutoPlugin] =
    flatten(ap.requires).toList collect {
      case Exclude(x) => x
    }
  // TODO - This doesn't handle nested AND boolean logic...
  private[sbt] def hasExclude(n: Plugins, p: AutoPlugin): Boolean = n match {
    case `p`          => false
    case Exclude(`p`) => true
    // TODO - This is stupidly advanced.  We do a nested check through possible and-ed
    // lists of plugins exclusions to see if the plugin ever winds up in an excluded=true case.
    // This would handle things like !!p or !(p && z)
    case Exclude(n) => hasInclude(n, p)
    case And(ns)    => ns.forall(n => hasExclude(n, p))
    case b: Basic   => false
    case Empty      => false
  }
  private[sbt] def hasInclude(n: Plugins, p: AutoPlugin): Boolean = n match {
    case `p`        => true
    case Exclude(n) => hasExclude(n, p)
    case And(ns)    => ns.forall(n => hasInclude(n, p))
    case b: Basic   => false
    case Empty      => false
  }
  private[this] def flattenConvert(n: Plugins): Seq[Literal] = n match {
    case And(ns)  => convertAll(ns)
    case b: Basic => convertBasic(b) :: Nil
    case Empty    => Nil
  }
  private[sbt] def flatten(n: Plugins): Seq[Basic] = n match {
    case And(ns)  => ns
    case b: Basic => b :: Nil
    case Empty    => Nil
  }

  private[this] def convert(n: Plugins): Formula = n match {
    case And(ns)  => convertAll(ns).reduce[Formula](_ && _)
    case b: Basic => convertBasic(b)
    case Empty    => Formula.True
  }
  private[this] def convertBasic(b: Basic): Literal = b match {
    case Exclude(n)    => !convertBasic(n)
    case a: AutoPlugin => Atom(a.label)
  }
  private[this] def convertAll(ns: Seq[Basic]): Seq[Literal] = ns map convertBasic

  /** True if the trigger clause `n` is satisfied by `model`. */
  def satisfied(n: Plugins, model: Set[AutoPlugin]): Boolean =
    flatten(n) forall {
      case Exclude(a)     => !model(a)
      case ap: AutoPlugin => model(ap)
    }

  private val autoImport = "autoImport"

  /** Determines whether a plugin has a stable autoImport member by:
   *
   * 1. Checking whether there exists a public field.
   * 2. Checking whether there exists a public object.
   *
   * The above checks work for inherited members too.
   *
   * @param ap The found plugin.
   * @param loader The plugin loader.
   * @return True if plugin has a stable member `autoImport`, otherwise false.
   */
  private[sbt] def hasAutoImportGetter(ap: AutoPlugin, loader: ClassLoader): Boolean = {
    import java.lang.reflect.Field
    import scala.util.control.Exception.catching
    // Make sure that we don't detect user-defined methods called autoImport
    def existsAutoImportVal(clazz: Class[_]): Option[Field] = {
      catching(classOf[NoSuchFieldException])
        .opt(clazz.getDeclaredField(autoImport))
        .orElse(Option(clazz.getSuperclass).flatMap(existsAutoImportVal))
    }

    val pluginClazz = ap.getClass
    existsAutoImportVal(pluginClazz)
      .orElse(
        catching(classOf[ClassNotFoundException]).opt(
          Class.forName(s"${pluginClazz.getName}$autoImport$$", false, loader)))
      .isDefined
  }

  /** Debugging method to time how long it takes to run various compilation tasks. */
  private[this] def timed[T](label: String, log: Logger)(t: => T): T = {
    val start = System.nanoTime
    val result = t
    val elapsed = System.nanoTime - start
    log.debug(label + " took " + (elapsed / 1e6) + " ms")
    result
  }
}
