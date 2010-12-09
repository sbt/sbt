/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt.parse

/**
* Represents a set of completions.
* It exists instead of implicitly defined operations on top of Set[Completion]
*  for laziness.
*/
sealed trait Completions
{
	def get: Set[Completion]
	final def x(o: Completions): Completions = Completions( for(cs <- get; os <- o.get) yield cs ++ os )
	final def ++(o: Completions): Completions = Completions( get ++ o.get )
	final def +:(o: Completion): Completions = Completions(get + o)
	final def filter(f: Completion => Boolean): Completions = Completions(get filter f)
	final def filterS(f: String => Boolean): Completions = filter(c => f(c.append))
	override def toString = get.mkString("Completions(",",",")")
	final def flatMap(f: Completion => Completions): Completions = Completions(get.flatMap(c => f(c).get))
	final def map(f: Completion => Completion): Completions = Completions(get map f)
}
object Completions
{
	/** Returns a lazy Completions instance using the provided Completion Set. */
	def apply(cs: => Set[Completion]): Completions = new Completions {
		lazy val get = cs
	}

	/** Returns a strict Completions instance using the provided Completion Set. */
	def strict(cs: Set[Completion]): Completions = new Completions {
		def get = cs
	}

	/** No suggested completions, not even the empty Completion.*/
	val nil: Completions = strict(Set.empty)

	/** Only includes the unmarked empty Completion as a suggestion. */
	val empty: Completions = strict(Set.empty + Completion.empty)

	/** Includes only the marked empty Completion as a suggestion. */
	val mark: Completions = strict(Set.empty + Completion.mark)

	/** Returns a strict Completions instance with a single Completion with `s` for `append`.*/
	def single(s: String): Completions = strict(Set.empty + Completion.strict("", s))
}

/**
* Represents a completion.
* The abstract members `prepend` and `append` are best explained with an example. 
*
* Assuming space-delimited tokens, processing this:
*   am is are w<TAB>
* could produce these Completions:
*   Completion { prepend = "w"; append = "as" }
*   Completion { prepend = "w"; append = "ere" }
* to suggest the tokens "was" and "were".
*
* In this way, two pieces of information are preserved:
*  1) what needs to be appended to the current input if a completion is selected
*  2) the full token being completed, which is useful for presenting a user with choices to select
*/
sealed trait Completion
{
	/** The part of the token that was in the input.*/
	def prepend: String

	/** The proposed suffix to append to the existing input to complete the last token in the input.*/
	def append: String

	/** True if this completion has been identified with a token.
	* A marked Completion will not be appended to another Completion unless that Completion is empty.
	* In this way, only a single token is completed at a time.*/
	def mark: Boolean
	
	final def isEmpty = prepend.isEmpty && append.isEmpty
	
	/** Appends the completions in `o` with the completions in this unless `o` is marked and this is nonempty.*/
	final def ++(o: Completion): Completion = if(o.mark && !isEmpty) this else Completion(prepend + o.prepend, append + o.append, mark)

	final def x(o: Completions): Completions = o.map(this ++ _)

	override final def toString = triple.toString
	override final lazy val hashCode = triple.hashCode
	override final def equals(o: Any) = o match {
		case c: Completion => triple == c.triple
		case _ => false
	}
	final def triple = (prepend, append, mark)
}
object Completion
{
	/** Constructs a lazy Completion with the given prepend, append, and mark values. */
	def apply(d: => String, a: => String, m: Boolean = false): Completion = new Completion {
		lazy val prepend = d
		lazy val append = a
		def mark = m
	}

	/** Constructs a strict Completion with the given prepend, append, and mark values. */
	def strict(d: String, a: String, m: Boolean = false): Completion = new Completion {
		def prepend = d
		def append = a
		def mark = m
	}

	/** An unmarked completion with the empty string for prepend and append. */
	val empty: Completion = strict("", "", false)

	/** A marked completion with the empty string for prepend and append. */
	val mark: Completion = Completion.strict("", "", true)
}