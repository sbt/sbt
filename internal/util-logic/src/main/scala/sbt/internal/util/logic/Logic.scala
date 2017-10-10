/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package logic

import scala.annotation.tailrec
import Formula.{ And, True }

/*
Defines a propositional logic with negation as failure and only allows stratified rule sets
(negation must be acyclic) in order to have a unique minimal model.

For example, this is not allowed:
  + p :- not q
  + q :- not p
but this is:
  + p :- q
  + q :- p
as is this:
  + p :- q
  + q := not r


 Some useful links:
  + https://en.wikipedia.org/wiki/Nonmonotonic_logic
  + https://en.wikipedia.org/wiki/Negation_as_failure
  + https://en.wikipedia.org/wiki/Propositional_logic
  + https://en.wikipedia.org/wiki/Stable_model_semantics
  + http://www.w3.org/2005/rules/wg/wiki/negation
 */

/** Disjunction (or) of the list of clauses. */
final case class Clauses(clauses: List[Clause]) {
  assert(clauses.nonEmpty, "At least one clause is required.")
}

/** When the `body` Formula succeeds, atoms in `head` are true. */
final case class Clause(body: Formula, head: Set[Atom])

/** A literal is an [[Atom]] or its [[negation|Negated]]. */
sealed abstract class Literal extends Formula {

  /** The underlying (positive) atom. */
  def atom: Atom

  /** Negates this literal.*/
  def unary_! : Literal

}

/** A variable with name `label`. */
final case class Atom(label: String) extends Literal {
  def atom = this
  def unary_! : Negated = Negated(this)
}

/**
 * A negated atom, in the sense of negation as failure, not logical negation.
 * That is, it is true if `atom` is not known/defined.
 */
final case class Negated(atom: Atom) extends Literal {
  def unary_! : Atom = atom
}

/**
 * A formula consists of variables, negation, and conjunction (and).
 * (Disjunction is not currently included- it is modeled at the level of a sequence of clauses.
 *  This is less convenient when defining clauses, but is not less powerful.)
 */
sealed abstract class Formula {

  /** Constructs a clause that proves `atoms` when this formula is true. */
  def proves(atom: Atom, atoms: Atom*): Clause = Clause(this, (atom +: atoms).toSet)

  /** Constructs a formula that is true iff this formula and `f` are both true.*/
  def &&(f: Formula): Formula = (this, f) match {
    case (True, x)                => x
    case (x, True)                => x
    case (And(as), And(bs))       => And(as ++ bs)
    case (And(as), b: Literal)    => And(as + b)
    case (a: Literal, And(bs))    => And(bs + a)
    case (a: Literal, b: Literal) => And(Set(a, b))
  }

}

object Formula {

  /** A conjunction of literals. */
  final case class And(literals: Set[Literal]) extends Formula {
    assert(literals.nonEmpty, "'And' requires at least one literal.")
  }

  final case object True extends Formula

}

object Logic {
  def reduceAll(
      clauses: List[Clause],
      initialFacts: Set[Literal]
  ): Either[LogicException, Matched] =
    reduce(Clauses(clauses), initialFacts)

  /**
   * Computes the variables in the unique stable model for the program represented by `clauses` and
   * `initialFacts`.  `clause` may not have any negative feedback (that is, negation is acyclic)
   * and `initialFacts` cannot be in the head of any clauses in `clause`.
   * These restrictions ensure that the logic program has a unique minimal model.
   */
  def reduce(clauses: Clauses, initialFacts: Set[Literal]): Either[LogicException, Matched] = {
    val (posSeq, negSeq) = separate(initialFacts.toSeq)
    val (pos, neg) = (posSeq.toSet, negSeq.toSet)

    val problem =
      checkContradictions(pos, neg) orElse
        checkOverlap(clauses, pos) orElse
        checkAcyclic(clauses)

    problem.toLeft(
      reduce0(clauses, initialFacts, Matched.empty)
    )
  }

  /**
   * Verifies `initialFacts` are not in the head of any `clauses`.
   * This avoids the situation where an atom is proved but no clauses prove it.
   * This isn't necessarily a problem, but the main sbt use cases expects
   * a proven atom to have at least one clause satisfied.
   */
  private[this] def checkOverlap(
      clauses: Clauses,
      initialFacts: Set[Atom]
  ): Option[InitialOverlap] = {
    val as = atoms(clauses)
    val initialOverlap = initialFacts.filter(as.inHead)
    if (initialOverlap.nonEmpty) Some(new InitialOverlap(initialOverlap)) else None
  }

  private[this] def checkContradictions(
      pos: Set[Atom],
      neg: Set[Atom]
  ): Option[InitialContradictions] = {
    val contradictions = pos intersect neg
    if (contradictions.nonEmpty) Some(new InitialContradictions(contradictions)) else None
  }

  private[this] def checkAcyclic(clauses: Clauses): Option[CyclicNegation] = {
    val deps = dependencyMap(clauses)
    val cycle = Dag.findNegativeCycle(graph(deps))
    if (cycle.nonEmpty) Some(new CyclicNegation(cycle)) else None
  }

  private[this] def graph(deps: Map[Atom, Set[Literal]]) = new Dag.DirectedSignedGraph[Atom] {
    type Arrow = Literal
    def nodes = deps.keys.toList
    def dependencies(a: Atom) = deps.getOrElse(a, Set.empty).toList

    def isNegative(b: Literal) = b match {
      case Negated(_) => true
      case Atom(_)    => false
    }

    def head(b: Literal) = b.atom
  }

  private[this] def dependencyMap(clauses: Clauses): Map[Atom, Set[Literal]] =
    (Map.empty[Atom, Set[Literal]] /: clauses.clauses) {
      case (m, Clause(formula, heads)) =>
        val deps = literals(formula)
        (m /: heads) { (n, head) =>
          n.updated(head, n.getOrElse(head, Set.empty) ++ deps)
        }
    }

  sealed abstract class LogicException(override val toString: String)

  final class InitialContradictions(val literals: Set[Atom])
      extends LogicException(
        "Initial facts cannot be both true and false:\n\t" + literals.mkString("\n\t")
      )

  final class InitialOverlap(val literals: Set[Atom])
      extends LogicException(
        "Initial positive facts cannot be implied by any clauses:\n\t" + literals.mkString("\n\t")
      )

  final class CyclicNegation(val cycle: List[Literal])
      extends LogicException(
        "Negation may not be involved in a cycle:\n\t" + cycle.mkString("\n\t")
      )

  /** Tracks proven atoms in the reverse order they were proved. */
  final class Matched private (val provenSet: Set[Atom], reverseOrdered: List[Atom]) {
    def add(atoms: Set[Atom]): Matched = add(atoms.toList)

    def add(atoms: List[Atom]): Matched = {
      val newOnly = atoms.filterNot(provenSet)
      new Matched(provenSet ++ newOnly, newOnly ::: reverseOrdered)
    }

    def ordered: List[Atom] = reverseOrdered.reverse
    override def toString = ordered.map(_.label).mkString("Matched(", ",", ")")
  }

  object Matched {
    val empty = new Matched(Set.empty, Nil)
  }

  /** Separates a sequence of literals into `(pos, neg)` atom sequences. */
  private[this] def separate(lits: Seq[Literal]): (Seq[Atom], Seq[Atom]) =
    Util.separate(lits) {
      case a: Atom    => Left(a)
      case Negated(n) => Right(n)
    }

  /**
   * Finds clauses that have no body and thus prove their head.
   * Returns `(<proven atoms>, <remaining unproven clauses>)`.
   */
  private[this] def findProven(c: Clauses): (Set[Atom], List[Clause]) = {
    val (proven, unproven) = c.clauses.partition(_.body == True)
    (proven.flatMap(_.head).toSet, unproven)
  }

  private[this] def keepPositive(lits: Set[Literal]): Set[Atom] =
    lits.collect { case a: Atom => a }.toSet

  // precondition: factsToProcess contains no contradictions
  @tailrec private[this] def reduce0(
      clauses: Clauses,
      factsToProcess: Set[Literal],
      state: Matched
  ): Matched =
    applyAll(clauses, factsToProcess) match {
      case None => state // all of the remaining clauses failed on the new facts
      case Some(applied) =>
        val (proven, unprovenClauses) = findProven(applied)
        val processedFacts = state add keepPositive(factsToProcess)
        val newlyProven = proven -- processedFacts.provenSet
        val newState = processedFacts add newlyProven
        if (unprovenClauses.isEmpty)
          newState // no remaining clauses, done.
        else {
          val unproven = Clauses(unprovenClauses)
          val nextFacts: Set[Literal] =
            if (newlyProven.nonEmpty) newlyProven.toSet else inferFailure(unproven)
          reduce0(unproven, nextFacts, newState)
        }
    }

  /**
   * Finds negated atoms under the negation as failure rule and returns them.
   * This should be called only after there are no more known atoms to be substituted.
   */
  private[this] def inferFailure(clauses: Clauses): Set[Literal] = {
    /* At this point, there is at least one clause and one of the following is the case as the
       result of the acyclic negation rule:
         i. there is at least one variable that occurs in a clause body but not in the head of a
            clause
         ii. there is at least one variable that occurs in the head of a clause and does not
             transitively depend on a negated variable

       In either case, each such variable x cannot be proven true and therefore proves 'not x'
       (negation as failure, !x in the code).
     */
    val allAtoms = atoms(clauses)
    val newFacts: Set[Literal] = negated(allAtoms.triviallyFalse)
    if (newFacts.nonEmpty)
      newFacts
    else {
      val possiblyTrue = hasNegatedDependency(clauses.clauses, Relation.empty, Relation.empty)
      val newlyFalse: Set[Literal] = negated(allAtoms.inHead -- possiblyTrue)
      if (newlyFalse.nonEmpty)
        newlyFalse
      else // should never happen due to the acyclic negation rule
        sys.error(s"No progress:\n\tclauses: $clauses\n\tpossibly true: $possiblyTrue")
    }
  }

  private[this] def negated(atoms: Set[Atom]): Set[Literal] = atoms.map(a => Negated(a))

  /**
   * Computes the set of atoms in `clauses` that directly or transitively take a negated atom as input.
   * For example, for the following clauses, this method would return `List(a, d)` :
   *  a :- b, not c
   *  d :- a
   */
  @tailrec
  def hasNegatedDependency(
      clauses: Seq[Clause],
      posDeps: Relation[Atom, Atom],
      negDeps: Relation[Atom, Atom]
  ): List[Atom] =
    clauses match {
      case Seq() =>
        // because cycles between positive literals are allowed, this isn't strictly a topological sort
        Dag.topologicalSortUnchecked(negDeps._1s)(posDeps.reverse)

      case Clause(formula, head) +: tail =>
        // collect direct positive and negative literals and track them in separate graphs
        val (pos, neg) = directDeps(formula)
        val (newPos, newNeg) = ((posDeps, negDeps) /: head) {
          case ((pdeps, ndeps), d) =>
            (pdeps + (d, pos), ndeps + (d, neg))
        }
        hasNegatedDependency(tail, newPos, newNeg)
    }

  /** Computes the `(positive, negative)` literals in `formula`. */
  private[this] def directDeps(formula: Formula): (Seq[Atom], Seq[Atom]) =
    Util.separate(literals(formula).toSeq) {
      case Negated(a) => Right(a)
      case a: Atom    => Left(a)
    }

  private[this] def literals(formula: Formula): Set[Literal] = formula match {
    case And(lits)  => lits
    case l: Literal => Set(l)
    case True       => Set.empty
  }

  /** Computes the atoms in the heads and bodies of the clauses in `clause`. */
  def atoms(cs: Clauses): Atoms = cs.clauses.map(c => Atoms(c.head, atoms(c.body))).reduce(_ ++ _)

  /** Computes the set of all atoms in `formula`. */
  def atoms(formula: Formula): Set[Atom] = formula match {
    case And(lits)    => lits.map(_.atom)
    case Negated(lit) => Set(lit)
    case a: Atom      => Set(a)
    case True         => Set()
  }

  /** Represents the set of atoms in the heads of clauses and in the bodies (formulas) of clauses. */
  final case class Atoms(inHead: Set[Atom], inFormula: Set[Atom]) {

    /** Concatenates this with `as`. */
    def ++(as: Atoms): Atoms = Atoms(inHead ++ as.inHead, inFormula ++ as.inFormula)

    /** Atoms that cannot be true because they do not occur in a head. */
    def triviallyFalse: Set[Atom] = inFormula -- inHead

  }

  /**
   * Applies known facts to `clause`s, deriving a new, possibly empty list of clauses.
   * 1. If a fact is in the body of a clause, the derived clause has that fact removed from the body.
   * 2. If the negation of a fact is in a body of a clause, that clause fails and is removed.
   * 3. If a fact or its negation is in the head of a clause, the derived clause has that fact (or its negation) removed from the head.
   * 4. If a head is empty, the clause proves nothing and is removed.
   *
   * NOTE: empty bodies do not cause a clause to succeed yet.
   *       All known facts must be applied before this can be done in order to avoid inconsistencies.
   * Precondition: no contradictions in `facts`
   * Postcondition: no atom in `facts` is present in the result
   * Postcondition: No clauses have an empty head
   */
  def applyAll(cs: Clauses, facts: Set[Literal]): Option[Clauses] = {
    val newClauses =
      if (facts.isEmpty)
        cs.clauses.filter(_.head.nonEmpty) // still need to drop clauses with an empty head
      else
        cs.clauses.map(c => applyAll(c, facts)).flatMap(_.toList)
    if (newClauses.isEmpty) None else Some(Clauses(newClauses))
  }

  def applyAll(c: Clause, facts: Set[Literal]): Option[Clause] = {
    val atoms = facts.map(_.atom)
    val newHead = c.head -- atoms // 3.
    if (newHead.isEmpty) // 4. empty head
      None
    else
      substitute(c.body, facts).map(f => Clause(f, newHead)) // 1, 2
  }

  /** Derives the formula that results from substituting `facts` into `formula`. */
  @tailrec def substitute(formula: Formula, facts: Set[Literal]): Option[Formula] = formula match {
    case And(lits) =>
      def negated(lits: Set[Literal]): Set[Literal] = lits.map(a => !a)
      if (lits.exists(negated(facts))) // 2.
        None
      else {
        val newLits = lits -- facts
        val newF = if (newLits.isEmpty) True else And(newLits)
        Some(newF) // 1.
      }
    case True => Some(True)
    case lit: Literal => // define in terms of And
      substitute(And(Set(lit)), facts)
  }
}
