package sbt

	import logic.{Atom, Clause, Clauses, Formula, Literal, Logic, Negated}
	import Logic.{CyclicNegation, InitialContradictions, InitialOverlap, LogicException}
	import Natures._

/** An expression that matches `Nature`s. */
sealed trait Natures {
	def && (o: Basic): Natures
}

/** Represents a feature or conceptual group of settings.
* `label` is the unique ID for this nature. */
final case class Nature(label: String) extends Basic {
	/** Constructs a Natures matcher that excludes this Nature. */
	def unary_! : Basic = Exclude(this)
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
			val byAtom = defined.map(x => (Atom(x.provides.label), x))
			val byAtomMap = byAtom.toMap
			if(byAtom.size != byAtomMap.size) duplicateProvidesError(byAtom)
			val clauses = Clauses( defined.map(d => asClause(d)) )
			requestedNatures =>
				Logic.reduce(clauses, flatten(requestedNatures).toSet) match {
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
	private[sbt] final case class Exclude(n: Nature) extends Basic  {
		def unary_! : Nature = n
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

	/** Defines a clause for `ap` such that the [[Nature]] provided by `ap` is the head and the selector for `ap` is the body. */
	private[sbt] def asClause(ap: AutoPlugin): Clause =
		Clause( convert(ap.select), Set(Atom(ap.provides.label)) )

	private[this] def flatten(n: Natures): Seq[Literal] = n match {
		case And(ns) => convertAll(ns)
		case b: Basic => convertBasic(b) :: Nil
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
	}
	private[this] def convertAll(ns: Seq[Basic]): Seq[Literal] = ns map convertBasic
}