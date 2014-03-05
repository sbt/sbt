package sbt
/*
TODO:
- index all available AutoPlugins to get the tasks that will be added
- error message when a task doesn't exist that it would be provided by plugin x, enabled by natures y,z, blocked by a, b
*/

	import logic.{Atom, Clause, Clauses, Formula, Literal, Logic, Negated}
	import Logic.{CyclicNegation, InitialContradictions, InitialOverlap, LogicException}
	import Def.Setting
	import Plugins._

/** Marks a top-level object so that sbt will wildcard import it for .sbt files, `consoleProject`, and `set`. */
trait AutoImport

/**
An AutoPlugin defines a group of settings and the conditions where the settings are automatically added to a build (called "activation").
The `select` method defines the conditions and a method like `projectSettings` defines the settings to add.

Steps for plugin authors:
1. Determine the [[AutoPlugins]]s that, when present (or absent), activate the AutoPlugin.
2. Determine the settings/configurations to automatically inject when activated.

For example, the following will automatically add the settings in `projectSettings`
  to a project that has both the `Web` and `Javascript` plugins enabled.

    object MyPlugin extends AutoPlugin {
        def select = Web && Javascript
        override def projectSettings = Seq(...)
    }

Steps for users:
1. Add dependencies on plugins in `project/plugins.sbt` as usual with `addSbtPlugin`
2. Add key plugins to Projects, which will automatically select the plugin + dependent plugin settings to add for those Projects.
3. Exclude plugins, if desired.

For example, given plugins Web and Javascript (perhaps provided by plugins added with addSbtPlugin),

  <Project>.plugins( Web && Javascript )

will activate `MyPlugin` defined above and have its settings automatically added.  If the user instead defines

  <Project>.plugins( Web && Javascript && !MyPlugin)

then the `MyPlugin` settings (and anything that activates only when `MyPlugin` is activated) will not be added.
*/
abstract class AutoPlugin extends Plugins.Basic
{
	/** This AutoPlugin will be activated for a project when the [[Plugins]] matcher returned by this method matches that project's plugins
	* AND the user does not explicitly exclude the Plugin returned by `provides`.
	*
	* For example, if this method returns `Web && Javascript`, this plugin instance will only be added
	* if the `Web` and `Javascript` plugins are enabled. */
	def select: Plugins

	val label: String = getClass.getName.stripSuffix("$")

	/** The [[Configuration]]s to add to each project that activates this AutoPlugin.*/
	def projectConfigurations: Seq[Configuration] = Nil

	/** The [[Setting]]s to add in the scope of each project that activates this AutoPlugin. */
	def projectSettings: Seq[Setting[_]] = Nil

	/** The [[Setting]]s to add to the build scope for each project that activates this AutoPlugin.
	* The settings returned here are guaranteed to be added to a given build scope only once
	* regardless of how many projects for that build activate this AutoPlugin. */
	def buildSettings: Seq[Setting[_]] = Nil

	/** The [[Setting]]s to add to the global scope exactly once if any project activates this AutoPlugin. */
	def globalSettings: Seq[Setting[_]] = Nil

	// TODO?: def commands: Seq[Command]

	def unary_! : Exclude = Exclude(this)


	/** If this plugin requries itself to be included, it means we're actually a nature,
	 * not a normal plugin.   The user must specifically enable this plugin
	 * but other plugins can rely on its existence. 
	 */
	final def isRoot: Boolean = 
	  this match {
	  	case _: RootAutoPlugin => true
	  	case _ => false
	  }
}
/**
 * A root AutoPlugin is a plugin which must be explicitly enabled by users in their `setPlugins` method
 * on a project.  However, RootAutoPlugins represent the "root" of a tree of dependent auto-plugins.
 */
abstract class RootAutoPlugin extends AutoPlugin {
	final def select: Plugins = this
}

/** An error that occurs when auto-plugins aren't configured properly.
* It translates the error from the underlying logic system to be targeted at end users. */
final class AutoPluginException private(val message: String, val origin: Option[LogicException]) extends RuntimeException(message)
{
	/** Prepends `p` to the error message derived from `origin`. */
	def withPrefix(p: String) = new AutoPluginException(p + message, origin)
}
object AutoPluginException
{
	def apply(msg: String): AutoPluginException = new AutoPluginException(msg, None)
	def apply(origin: LogicException): AutoPluginException = new AutoPluginException(Plugins.translateMessage(origin), Some(origin))
}

/** An expression that matches `AutoPlugin`s. */
sealed trait Plugins {
	def && (o: Basic): Plugins
}

object Plugins
{
	/** Given the available auto plugins `defined`, returns a function that selects [[AutoPlugin]]s for the provided [[AutoPlugin]]s.
	* The [[AutoPlugin]]s are topologically sorted so that a selected [[AutoPlugin]] comes before its selecting [[AutoPlugin]].*/
	def compile(defined: List[AutoPlugin]): Plugins => Seq[AutoPlugin] =
		if(defined.isEmpty)
			Types.const(Nil)
		else
		{
			val byAtom = defined.map(x => (Atom(x.label), x))
			val byAtomMap = byAtom.toMap
			if(byAtom.size != byAtomMap.size) duplicateProvidesError(byAtom)
			// Ignore clauses for plugins that just require themselves be specified.
			// Avoids the requirement for pure Nature strings *and* possible
			// circular dependencies in the logic.
			val clauses = Clauses( defined.filterNot(_.isRoot).map(d => asClause(d)) )
			requestedPlugins =>
				Logic.reduce(clauses, flattenConvert(requestedPlugins).toSet) match {
					case Left(problem) => throw AutoPluginException(problem)
					case Right(results) =>
						// results includes the originally requested (positive) atoms,
						//   which won't have a corresponding AutoPlugin to map back to
						results.ordered.flatMap(a => byAtomMap.get(a).toList)
				}
		}

	private[sbt] def translateMessage(e: LogicException) = e match {
		case ic: InitialContradictions => s"Contradiction in selected plugins.  These plguins were both included and excluded: ${literalsString(ic.literals.toSeq)}"
		case io: InitialOverlap => s"Cannot directly enable plugins.  Plugins are enabled when their required plugins are satisifed.  The directly selected plugins were: ${literalsString(io.literals.toSeq)}"
		case cn: CyclicNegation => s"Cycles in plugin requirements cannot involve excludes.  The problematic cycle is: ${literalsString(cn.cycle)}"
	}
	private[this] def literalsString(lits: Seq[Literal]): String =
		lits map { case Atom(l) => l; case Negated(Atom(l)) => l } mkString(", ")

	private[this] def duplicateProvidesError(byAtom: Seq[(Atom, AutoPlugin)]) {
		val dupsByAtom = byAtom.groupBy(_._1).mapValues(_.map(_._2))
		val dupStrings = for( (atom, dups) <- dupsByAtom if dups.size > 1 ) yield
			s"${atom.label} by ${dups.mkString(", ")}"
		val (ns, nl) = if(dupStrings.size > 1) ("s", "\n\t") else ("", " ")
		val message = s"Plugin$ns provided by multiple AutoPlugins:$nl${dupStrings.mkString(nl)}"
		throw AutoPluginException(message)
	}

	/** [[Plugins]] instance that doesn't require any [[Plugins]]s. */
	def empty: Plugins = Empty
	private[sbt] final object Empty extends Plugins {
		def &&(o: Basic): Plugins = o
		override def toString = "<none>"
	}

	/** An included or excluded Nature/Plugin.  TODO: better name than Basic.  Also, can we dump
	 *  this class.
	 */
	sealed abstract class Basic extends Plugins {
		def &&(o: Basic): Plugins = And(this :: o :: Nil)
	}
	private[sbt] final case class Exclude(n: AutoPlugin) extends Basic  {
		override def toString = s"!$n"
	}
	private[sbt] final case class And(plugins: List[Basic]) extends Plugins {
		def &&(o: Basic): Plugins = And(o :: plugins)
		override def toString = plugins.mkString(", ")
	}
	private[sbt] def and(a: Plugins, b: Plugins) = b match {
		case Empty => a
		case And(ns) => (a /: ns)(_ && _)
		case b: Basic => a && b
	}
	private[sbt] def remove(a: Plugins, del: Set[Basic]): Plugins = a match {
		case b: Basic => if(del(b)) Empty else b
		case Empty => Empty
		case And(ns) =>
			val removed = ns.filterNot(del)
			if(removed.isEmpty) Empty else And(removed)
	}

	/** Defines a clause for `ap` such that the [[AutPlugin]] provided by `ap` is the head and the selector for `ap` is the body. */
	private[sbt] def asClause(ap: AutoPlugin): Clause =
		Clause( convert(ap.select), Set(Atom(ap.label)) )

	private[this] def flattenConvert(n: Plugins): Seq[Literal] = n match {
		case And(ns) => convertAll(ns)
		case b: Basic => convertBasic(b) :: Nil
		case Empty => Nil
	}
	private[sbt] def flatten(n: Plugins): Seq[Basic] = n match {
		case And(ns) => ns
		case b: Basic => b :: Nil
		case Empty => Nil
	}

	private[this] def convert(n: Plugins): Formula = n match {
		case And(ns) => convertAll(ns).reduce[Formula](_ && _)
		case b: Basic => convertBasic(b)
		case Empty => Formula.True
	}
	private[this] def convertBasic(b: Basic): Literal = b match {
		case Exclude(n) => !convertBasic(n)
		case a: AutoPlugin => Atom(a.label)
	}
	private[this] def convertAll(ns: Seq[Basic]): Seq[Literal] = ns map convertBasic

	/** True if the select clause `n` is satisifed by `model`. */
	def satisfied(n: Plugins, model: Set[AutoPlugin]): Boolean =
		flatten(n) forall {
			case Exclude(a) => !model(a)
			case ap: AutoPlugin => model(ap)
		}
}