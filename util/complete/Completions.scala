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
	override final def hashCode = get.hashCode
	override final def equals(o: Any) = o match { case c: Completions => get == c.get; case _ => false }
}
object Completions
{
	/** Returns a lazy Completions instance using the provided Completion Set. */
	def apply(cs: => Set[Completion]): Completions = new Completions {
		lazy val get = cs
	}

	/** Returns a strict Completions instance using the provided Completion Set. */
	def strict(cs: Set[Completion]): Completions = apply(cs)

	/** No suggested completions, not even the empty Completion.*/
	val nil: Completions = strict(Set.empty)

	/** Only includes an empty Suggestion */
	val empty: Completions = strict(Set.empty + Completion.empty)

	/** Returns a strict Completions instance containing only the provided Completion.*/
	def single(c: Completion): Completions = strict(Set.empty + c)
}

/**
* Represents a completion.
* The abstract members `display` and `append` are best explained with an example. 
*
* Assuming space-delimited tokens, processing this:
*   am is are w<TAB>
* could produce these Completions:
*   Completion { display = "was"; append = "as" }
*   Completion { display = "were"; append = "ere" }
* to suggest the tokens "was" and "were".
*
* In this way, two pieces of information are preserved:
*  1) what needs to be appended to the current input if a completion is selected
*  2) the full token being completed, which is useful for presenting a user with choices to select
*/
sealed trait Completion
{
	/** The proposed suffix to append to the existing input to complete the last token in the input.*/
	def append: String
	/** The string to present to the user to represent the full token being suggested.*/
	def display: String
	/** True if this Completion is suggesting the empty string.*/
	def isEmpty: Boolean

	/** Appends the completions in `o` with the completions in this Completion.*/
	def ++(o: Completion): Completion = Completion.concat(this, o)
	final def x(o: Completions): Completions = o.map(this ++ _)
	override final lazy val hashCode = Completion.hashCode(this)
	override final def equals(o: Any) = o match { case c: Completion => Completion.equal(this, c); case _ => false }
}
final class DisplayOnly(display0: String) extends Completion
{
	lazy val display = display0
	def isEmpty = display.isEmpty
	def append = ""
	override def toString = "{" + display + "}"
}
final class Token(prepend0: String, append0: String) extends Completion
{
	lazy val prepend = prepend0
	lazy val append = append0
	def isEmpty = prepend.isEmpty && append.isEmpty
	def display = prepend + append
	override final def toString = "[" + prepend + "," + append +"]"
}
final class Suggestion(append0: String) extends Completion
{
	lazy val append = append0
	def isEmpty = append.isEmpty
	def display = append
	override def toString = append
}
object Completion
{
	def concat(a: Completion, b: Completion): Completion =
		(a,b) match
		{
			case (as: Suggestion, bs: Suggestion) => suggestion(as.append + bs.append)
			case (at: Token, _) if at.append.isEmpty => b
			case _ if a.isEmpty => b
			case _ => a
		}

	def equal(a: Completion, b: Completion): Boolean =
		(a,b) match
		{
			case (as: Suggestion, bs: Suggestion) => as.append == bs.append
			case (ad: DisplayOnly, bd: DisplayOnly) => ad.display == bd.display
			case (at: Token, bt: Token) => at.prepend == bt.prepend && at.append == bt.append
			case _ => false
		}

	def hashCode(a: Completion): Int =
		a match
		{
			case as: Suggestion => (0, as.append).hashCode
			case ad: DisplayOnly => (1, ad.display).hashCode
			case at: Token => (2, at.prepend, at.append).hashCode
		}

	val empty: Completion = suggestStrict("")
	def single(c: Char): Completion = suggestStrict(c.toString)
	
	def displayOnly(value: => String): Completion = new DisplayOnly(value)
	def displayStrict(value: String): Completion = displayOnly(value)
	def token(prepend: => String, append: => String): Completion = new Token(prepend, append)
	def tokenStrict(prepend: String, append: String): Completion = token(prepend, append)
	def suggestion(value: => String): Completion = new Suggestion(value)
	def suggestStrict(value: String): Completion = suggestion(value)
}