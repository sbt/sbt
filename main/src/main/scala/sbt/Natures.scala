package sbt
/*
TODO:
- index all available AutoPlugins to get the tasks that will be added
- error message when a task doesn't exist that it would be provided by plugin x, enabled by natures y,z, blocked by a, b
*/

	import logic.{Atom, Clause, Clauses, Formula, Literal, Logic, Negated}
	import Logic.{CyclicNegation, InitialContradictions, InitialOverlap, LogicException}
	import Def.Setting
	import Natures._

/** Marks a top-level object so that sbt will wildcard import it for .sbt files, `consoleProject`, and `set`. */
trait AutoImport

/**
An AutoPlugin defines a group of settings and the conditions where the settings are automatically added to a build (called "activation").
The `select` method defines the conditions and a method like `projectSettings` defines the settings to add.

Steps for plugin authors:
1. Determine the [[Nature]]s that, when present (or absent), activate the AutoPlugin.
2. Determine the settings/configurations to automatically inject when activated.

For example, the following will automatically add the settings in `projectSettings`
  to a project that has both the `Web` and `Javascript` natures enabled.

    object MyPlugin extends AutoPlugin {
        def select = Web && Javascript
        override def projectSettings = Seq(...)
    }

Steps for users:
1. Add dependencies on plugins as usual with addSbtPlugin
2. Add Natures to Projects, which will automatically select the plugin settings to add for those Projects.
3. Exclude plugins, if desired.

For example, given natures Web and Javascript (perhaps provided by plugins added with addSbtPlugin),

  <Project>.natures( Web && Javascript )

will activate `MyPlugin` defined above and have its settings automatically added.  If the user instead defines

  <Project>.natures( Web && Javascript && !MyPlugin)

then the `MyPlugin` settings (and anything that activates only when `MyPlugin` is activated) will not be added.
*/
abstract class AutoPlugin extends Natures.Basic
{
	/** This AutoPlugin will be activated for a project when the [[Natures]] matcher returned by this method matches that project's natures
	* AND the user does not explicitly exclude the Nature returned by `provides`.
	*
	* For example, if this method returns `Web && Javascript`, this plugin instance will only be added
	* if the `Web` and `Javascript` natures are enabled. */
	def select: Natures

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
	def apply(origin: LogicException): AutoPluginException = new AutoPluginException(Natures.translateMessage(origin), Some(origin))
}

/** An expression that matches `Nature`s. */
sealed trait Natures {
	def && (o: Basic): Natures
}

/** Represents a feature or conceptual group of settings.
* `label` is the unique ID for this nature. */
final case class Nature(label: String) extends Basic {
	/** Constructs a Natures matcher that excludes this Nature. */
	override def toString = label
}

object Natures
{
	/** Given the available auto plugins `defined`, returns a function that selects [[AutoPlugin]]s for the provided [[Nature]]s.
	* The [[AutoPlugin]]s are topologically sorted so that a selected [[AutoPlugin]] comes before its selecting [[AutoPlugin]].*/
	def compile(defined: List[AutoPlugin]): Natures => Seq[AutoPlugin] =
		if(defined.isEmpty)
			Types.const(Nil)
		else
		{
			val byAtom = defined.map(x => (Atom(x.label), x))
			val byAtomMap = byAtom.toMap
			if(byAtom.size != byAtomMap.size) duplicateProvidesError(byAtom)
			val clauses = Clauses( defined.map(d => asClause(d)) )
			requestedNatures =>
				Logic.reduce(clauses, flattenConvert(requestedNatures).toSet) match {
					case Left(problem) => throw AutoPluginException(problem)
					case Right(results) =>
						// results includes the originally requested (positive) atoms,
						//   which won't have a corresponding AutoPlugin to map back to
						results.ordered.flatMap(a => byAtomMap.get(a).toList)
				}
		}

	private[sbt] def translateMessage(e: LogicException) = e match {
		case ic: InitialContradictions => s"Contradiction in selected natures.  These natures were both included and excluded: ${literalsString(ic.literals.toSeq)}"
		case io: InitialOverlap => s"Cannot directly enable plugins.  Plugins are enabled when their required natures are satisifed.  The directly selected plugins were: ${literalsString(io.literals.toSeq)}"
		case cn: CyclicNegation => s"Cycles in plugin requirements cannot involve excludes.  The problematic cycle is: ${literalsString(cn.cycle)}"
	}
	private[this] def literalsString(lits: Seq[Literal]): String =
		lits map { case Atom(l) => l; case Negated(Atom(l)) => l } mkString(", ")

	private[this] def duplicateProvidesError(byAtom: Seq[(Atom, AutoPlugin)]) {
		val dupsByAtom = byAtom.groupBy(_._1).mapValues(_.map(_._2))
		val dupStrings = for( (atom, dups) <- dupsByAtom if dups.size > 1 ) yield
			s"${atom.label} by ${dups.mkString(", ")}"
		val (ns, nl) = if(dupStrings.size > 1) ("s", "\n\t") else ("", " ")
		val message = s"Nature$ns provided by multiple AutoPlugins:$nl${dupStrings.mkString(nl)}"
		throw AutoPluginException(message)
	}

	/** [[Natures]] instance that doesn't require any [[Nature]]s. */
	def empty: Natures = Empty
	private[sbt] final object Empty extends Natures {
		def &&(o: Basic): Natures = o
		override def toString = "<none>"
	}

	/** An included or excluded Nature.  TODO: better name than Basic. */
	sealed abstract class Basic extends Natures {
		def &&(o: Basic): Natures = And(this :: o :: Nil)
	}
	private[sbt] final case class Exclude(n: AutoPlugin) extends Basic  {
		override def toString = s"!$n"
	}
	private[sbt] final case class And(natures: List[Basic]) extends Natures {
		def &&(o: Basic): Natures = And(o :: natures)
		override def toString = natures.mkString(", ")
	}
	private[sbt] def and(a: Natures, b: Natures) = b match {
		case Empty => a
		case And(ns) => (a /: ns)(_ && _)
		case b: Basic => a && b
	}
	private[sbt] def remove(a: Natures, del: Set[Basic]): Natures = a match {
		case b: Basic => if(del(b)) Empty else b
		case Empty => Empty
		case And(ns) =>
			val removed = ns.filterNot(del)
			if(removed.isEmpty) Empty else And(removed)
	}

	/** Defines a clause for `ap` such that the [[Nature]] provided by `ap` is the head and the selector for `ap` is the body. */
	private[sbt] def asClause(ap: AutoPlugin): Clause =
		Clause( convert(ap.select), Set(Atom(ap.label)) )

	private[this] def flattenConvert(n: Natures): Seq[Literal] = n match {
		case And(ns) => convertAll(ns)
		case b: Basic => convertBasic(b) :: Nil
		case Empty => Nil
	}
	private[sbt] def flatten(n: Natures): Seq[Basic] = n match {
		case And(ns) => ns
		case b: Basic => b :: Nil
		case Empty => Nil
	}

	private[this] def convert(n: Natures): Formula = n match {
		case And(ns) => convertAll(ns).reduce[Formula](_ && _)
		case b: Basic => convertBasic(b)
		case Empty => Formula.True
	}
	private[this] def convertBasic(b: Basic): Literal = b match {
		case Exclude(n) => !convertBasic(n)
		case Nature(s) => Atom(s)
		case a: AutoPlugin => Atom(a.label)
	}
	private[this] def convertAll(ns: Seq[Basic]): Seq[Literal] = ns map convertBasic

	/** True if the select clause `n` is satisifed by `model`. */
	def satisfied(n: Natures, model: Set[AutoPlugin], natures: Set[Nature]): Boolean =
		flatten(n) forall {
			case Exclude(a) => !model(a)
			case n: Nature => natures(n)
			case ap: AutoPlugin => model(ap)
		}
}