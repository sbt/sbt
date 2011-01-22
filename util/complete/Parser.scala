/* sbt -- Simple Build Tool
 * Copyright 2008, 2010, 2011  Mark Harrah
 */
package sbt.complete

	import Parser._

sealed trait Parser[+T]
{
	def derive(i: Char): Parser[T]
	def resultEmpty: Option[T]
	def result: Option[T] = None
	def completions: Completions
	def valid: Boolean
	def isTokenStart = false
}
sealed trait RichParser[A]
{
	/** Produces a Parser that applies the original Parser and then applies `next` (in order).*/
	def ~[B](next: Parser[B]): Parser[(A,B)]
	/** Produces a Parser that applies the original Parser one or more times.*/
	def + : Parser[Seq[A]]
	/** Produces a Parser that applies the original Parser zero or more times.*/
	def * : Parser[Seq[A]]
	/** Produces a Parser that applies the original Parser zero or one times.*/
	def ? : Parser[Option[A]]
	/** Produces a Parser that applies either the original Parser or `next`.*/
	def |[B >: A](b: Parser[B]): Parser[B]
	/** Produces a Parser that applies either the original Parser or `next`.*/
	def ||[B](b: Parser[B]): Parser[Either[A,B]]
	/** Produces a Parser that applies the original Parser to the input and then applies `f` to the result.*/
	def map[B](f: A => B): Parser[B]
	/** Returns the original parser.  This is useful for converting literals to Parsers.
	* For example, `'c'.id` or `"asdf".id`*/
	def id: Parser[A]

	def ^^^[B](value: B): Parser[B]
	def ??[B >: A](alt: B): Parser[B]
	def <~[B](b: Parser[B]): Parser[A]
	def ~>[B](b: Parser[B]): Parser[B]
	
	def unary_- : Parser[Unit]
	def & (o: Parser[_]): Parser[A]
	def - (o: Parser[_]): Parser[A]
	/** Explicitly defines the completions for the original Parser.*/
	def examples(s: String*): Parser[A]
	/** Explicitly defines the completions for the original Parser.*/
	def examples(s: Set[String]): Parser[A]
	/** Converts a Parser returning a Char sequence to a Parser returning a String.*/
	def string(implicit ev: A <:< Seq[Char]): Parser[String]
	/** Produces a Parser that filters the original parser.
	* If 'f' is not true when applied to the output of the original parser, the Parser returned by this method fails.*/
	def filter(f: A => Boolean): Parser[A]

	def flatMap[B](f: A => Parser[B]): Parser[B]
}
object Parser extends ParserMain
{

	def checkMatches(a: Parser[_], completions: Seq[String])
	{
		val bad = completions.filter( apply(a)(_).resultEmpty.isEmpty)
		if(!bad.isEmpty) error("Invalid example completions: " + bad.mkString("'", "', '", "'"))
	}

	def mapParser[A,B](a: Parser[A], f: A => B): Parser[B] =
		if(a.valid) {
			a.result match
			{
				case Some(av) => success( f(av) )
				case None => new MapParser(a, f)
			}
		}
		else Invalid

	def bindParser[A,B](a: Parser[A], f: A => Parser[B]): Parser[B] =
		if(a.valid) {
			a.result match
			{
				case Some(av) => f(av)
				case None => new BindParser(a, f)
			}
		}
		else Invalid

	def filterParser[T](a: Parser[T], f: T => Boolean): Parser[T] =
		if(a.valid) {
			a.result match
			{
				case Some(av) => if( f(av) ) successStrict( av ) else Invalid
				case None => new Filter(a, f)
			}
		}
		else Invalid

	def seqParser[A,B](a: Parser[A], b: Parser[B]): Parser[(A,B)] =
		if(a.valid && b.valid)
			(a.result, b.result) match {
				case (Some(av), Some(bv)) => successStrict( (av, bv) )
				case (Some(av), None) => b map { bv => (av, bv) }
				case (None, Some(bv)) => a map { av => (av, bv) }
				case (None, None) => new SeqParser(a,b)
			}
		else Invalid

	def choiceParser[A,B](a: Parser[A], b: Parser[B]): Parser[Either[A,B]] =
		if(a.valid)
			if(b.valid) new HetParser(a,b) else a.map( Left(_) )
		else
			b.map( Right(_) )

	def opt[T](a: Parser[T]): Parser[Option[T]] =
		if(a.valid) new Optional(a) else successStrict(None)

	def zeroOrMore[T](p: Parser[T]): Parser[Seq[T]] = repeat(p, 0, Infinite)
	def oneOrMore[T](p: Parser[T]): Parser[Seq[T]] = repeat(p, 1, Infinite)

	def repeat[T](p: Parser[T], min: Int = 0, max: UpperBound = Infinite): Parser[Seq[T]] =
		repeat(None, p, min, max, Nil)
	private[complete] def repeat[T](partial: Option[Parser[T]], repeated: Parser[T], min: Int, max: UpperBound, revAcc: List[T]): Parser[Seq[T]] =
	{
		assume(min >= 0, "Minimum must be greater than or equal to zero (was " + min + ")")
		assume(max >= min, "Minimum must be less than or equal to maximum (min: " + min + ", max: " + max + ")")

		def checkRepeated(invalidButOptional: => Parser[Seq[T]]): Parser[Seq[T]] =
			if(repeated.valid)
				repeated.result match
				{
					case Some(value) => successStrict(revAcc reverse_::: value :: Nil) // revAcc should be Nil here
					case None => if(max.isZero) successStrict(revAcc.reverse) else new Repeat(partial, repeated, min, max, revAcc)
				}
			else if(min == 0)
				invalidButOptional
			else
				Invalid
		
		partial match
		{
			case Some(part) =>
				if(part.valid)
					part.result match
					{
						case Some(value) => repeat(None, repeated, min, max, value :: revAcc)
						case None => checkRepeated(part.map(lv => (lv :: revAcc).reverse))
					}
				else Invalid
			case None => checkRepeated(successStrict(Nil))
		}
	}

	def sub[T](a: Parser[T], b: Parser[_]): Parser[T]  =  and(a, not(b))

	def and[T](a: Parser[T], b: Parser[_]): Parser[T]  =  if(a.valid && b.valid) new And(a, b) else Invalid
}
trait ParserMain
{
	implicit def richParser[A](a: Parser[A]): RichParser[A] = new RichParser[A]
	{
		def ~[B](b: Parser[B]) = seqParser(a, b)
		def ||[B](b: Parser[B]) = choiceParser(a,b)
		def |[B >: A](b: Parser[B]) = homParser(a,b)
		def ? = opt(a)
		def * = zeroOrMore(a)
		def + = oneOrMore(a)
		def map[B](f: A => B) = mapParser(a, f)
		def id = a

		def ^^^[B](value: B): Parser[B] = a map { _ => value }
		def ??[B >: A](alt: B): Parser[B] = a.? map { _ getOrElse alt }
		def <~[B](b: Parser[B]): Parser[A] = (a ~ b) map { case av ~ _ => av }
		def ~>[B](b: Parser[B]): Parser[B] = (a ~ b) map { case _ ~ bv => bv }
	
		def unary_- = not(a)
		def & (o: Parser[_]) = and(a, o)
		def - (o: Parser[_]) = sub(a, o)
		def examples(s: String*): Parser[A] = examples(s.toSet)
		def examples(s: Set[String]): Parser[A] = Parser.examples(a, s, check = true)
		def filter(f: A => Boolean): Parser[A] = filterParser(a, f)
		def string(implicit ev: A <:< Seq[Char]): Parser[String] = map(_.mkString)
		def flatMap[B](f: A => Parser[B]) = bindParser(a, f)
	}
	implicit def literalRichParser(c: Char): RichParser[Char] = richParser(c)
	implicit def literalRichParser(s: String): RichParser[String] = richParser(s)

	def failure[T](msg: String): Parser[T] = Invalid(msg)
	def successStrict[T](value: T): Parser[T] = success(value)
	def success[T](value: => T): Parser[T] = new ValidParser[T] {
		private[this] lazy val v = value
		override def result = Some(v)
		def resultEmpty = result
		def derive(c: Char) = Invalid
		def completions = Completions.empty
		override def toString = "success(" + v + ")"
	}

	implicit def range(r: collection.immutable.NumericRange[Char]): Parser[Char] =
		new CharacterClass(r contains _).examples(r.map(_.toString) : _*)
	def chars(legal: String): Parser[Char] =
	{
		val set = legal.toSet
		new CharacterClass(set) examples(set.map(_.toString))
	}
	def charClass(f: Char => Boolean): Parser[Char] = new CharacterClass(f)

	implicit def literal(ch: Char): Parser[Char] = new ValidParser[Char] {
		def resultEmpty = None
		def derive(c: Char) = if(c == ch) successStrict(ch) else Invalid
		def completions = Completions.single(Completion.suggestStrict(ch.toString))
		override def toString = "'" + ch + "'"
	}
	implicit def literal(s: String): Parser[String] = stringLiteral(s, s.toList)
	object ~ {
		def unapply[A,B](t: (A,B)): Some[(A,B)] = Some(t)
	}

	// intended to be temporary pending proper error feedback
	def result[T](p: Parser[T], s: String): Either[(String,Int), T] =
	{
	/*	def loop(i: Int, a: Parser[T]): Either[(String,Int), T] =
			a.err match
			{
				case Some(msg) => Left((msg, i))
				case None =>
					val ci = i+1
					if(ci >= s.length)
						a.resultEmpty.toRight(("", i))
					else
						loop(ci, a derive s(ci))
			}
		loop(-1, p)*/
		apply(p)(s).resultEmpty.toRight(("Parse error", 0))
	}

	def apply[T](p: Parser[T])(s: String): Parser[T] =
		(p /: s)(derive1)

	def derive1[T](p: Parser[T], c: Char): Parser[T] =
		if(p.valid) p.derive(c) else p

	// The x Completions.empty removes any trailing token completions where append.isEmpty
	def completions(p: Parser[_], s: String): Completions = apply(p)(s).completions x Completions.empty

	def examples[A](a: Parser[A], completions: Set[String], check: Boolean = false): Parser[A] =
		if(a.valid) {
			a.result match
			{
				case Some(av) => successStrict( av )
				case None =>
					if(check) checkMatches(a, completions.toSeq)
					new Examples(a, completions)
			}
		}
		else a

	def matched(t: Parser[_], seenReverse: List[Char] = Nil, partial: Boolean = false): Parser[String] =
		if(!t.valid)
			if(partial && !seenReverse.isEmpty) successStrict(seenReverse.reverse.mkString) else Invalid
		else if(t.result.isEmpty)
			new MatchedString(t, seenReverse, partial)
		else
			successStrict(seenReverse.reverse.mkString)

	def token[T](t: Parser[T]): Parser[T] = token(t, "", true)
	def token[T](t: Parser[T], description: String): Parser[T] = token(t, description, false)
	def token[T](t: Parser[T], seen: String, track: Boolean): Parser[T] =
		if(t.valid && !t.isTokenStart)
			if(t.result.isEmpty) new TokenStart(t, seen, track) else t
		else
			t

	def homParser[A](a: Parser[A], b: Parser[A]): Parser[A] =
		if(a.valid)
			if(b.valid) new HomParser(a, b) else a
		else
			b

	def not(p: Parser[_]): Parser[Unit] = new Not(p)

	def stringLiteral(s: String, remaining: List[Char]): Parser[String] =
		if(s.isEmpty) error("String literal cannot be empty") else if(remaining.isEmpty) success(s) else new StringLiteral(s, remaining)
}
sealed trait ValidParser[T] extends Parser[T]
{
	final def valid = true
}
private object Invalid extends Invalid("inv")
private sealed case class Invalid(val message: String) extends Parser[Nothing]
{
	def resultEmpty = None
	def derive(c: Char) = error("Invalid.")
	override def valid = false
	def completions = Completions.nil
	override def toString = message
}
private final class SeqParser[A,B](a: Parser[A], b: Parser[B]) extends ValidParser[(A,B)]
{
	def cross(ao: Option[A], bo: Option[B]): Option[(A,B)] = for(av <- ao; bv <- bo) yield (av,bv)
	lazy val resultEmpty = cross(a.resultEmpty, b.resultEmpty)
	def derive(c: Char) =
	{
		val common = a.derive(c) ~ b
		a.resultEmpty match
		{
			case Some(av) => common | b.derive(c).map(br => (av,br))
			case None => common
		}
	}
	lazy val completions = a.completions x b.completions
	override def toString = "(" + a + " ~ " + b + ")"
}

private final class HomParser[A](a: Parser[A], b: Parser[A]) extends ValidParser[A]
{
	def derive(c: Char) = (a derive c) | (b derive c)
	lazy val resultEmpty = a.resultEmpty orElse b.resultEmpty
	lazy val completions = a.completions ++ b.completions
	override def toString = "(" + a + " | " + b + ")"
}
private final class HetParser[A,B](a: Parser[A], b: Parser[B]) extends ValidParser[Either[A,B]]
{
	def derive(c: Char) = (a derive c) || (b derive c)
	lazy val resultEmpty = a.resultEmpty.map(Left(_)) orElse b.resultEmpty.map(Right(_))
	lazy val completions = a.completions ++ b.completions
	override def toString = "(" + a + " || " + b + ")"
}
private final class BindParser[A,B](a: Parser[A], f: A => Parser[B]) extends ValidParser[B]
{
	lazy val resultEmpty = a.resultEmpty match { case None => None; case Some(av) => f(av).resultEmpty }
	lazy val completions =
		a.completions flatMap { c =>
			apply(a)(c.append).resultEmpty match {
				case None => Completions.strict(Set.empty + c)
				case Some(av) => c x f(av).completions
			}
		}

	def derive(c: Char) =
	{
		val common = a derive c flatMap f
		a.resultEmpty match
		{
			case Some(av) => common | derive1(f(av), c)
			case None => common
		}
	}
	override def isTokenStart = a.isTokenStart
	override def toString = "bind(" + a + ")"
}
private final class MapParser[A,B](a: Parser[A], f: A => B) extends ValidParser[B]
{
	lazy val resultEmpty = a.resultEmpty map f
	def derive(c: Char) = (a derive c) map f
	def completions = a.completions
	override def isTokenStart = a.isTokenStart
	override def toString = "map(" + a + ")"
}
private final class Filter[T](p: Parser[T], f: T => Boolean) extends ValidParser[T]
{
	lazy val resultEmpty = p.resultEmpty filter f
	def derive(c: Char) = (p derive c) filter f
	lazy val completions = p.completions filterS { s => apply(p)(s).resultEmpty.filter(f).isDefined }
	override def toString = "filter(" + p + ")"
	override def isTokenStart = p.isTokenStart
}
private final class MatchedString(delegate: Parser[_], seenReverse: List[Char], partial: Boolean) extends ValidParser[String]
{
	lazy val seen = seenReverse.reverse.mkString
	def derive(c: Char) = matched(delegate derive c, c :: seenReverse, partial)
	def completions = delegate.completions
	def resultEmpty = if(delegate.resultEmpty.isDefined) Some(seen) else if(partial) Some(seen) else None
	override def isTokenStart = delegate.isTokenStart
	override def toString = "matched(" + partial + ", " + seen + ", " + delegate + ")"
}
private final class TokenStart[T](delegate: Parser[T], seen: String, track: Boolean) extends ValidParser[T]
{
	def derive(c: Char) = token( delegate derive c, if(track) seen + c else seen, track)
	lazy val completions =
		if(track)
		{
			val dcs = delegate.completions
			Completions( for(c <- dcs.get) yield Completion.token(seen, c.append) )
		}
		else
			Completions.single(Completion.displayStrict(seen))

	def resultEmpty = delegate.resultEmpty
	override def isTokenStart = true
	override def toString = "token('" + seen + "', " + track + ", " + delegate + ")"
}
private final class And[T](a: Parser[T], b: Parser[_]) extends ValidParser[T]
{
	def derive(c: Char) = (a derive c) & (b derive c)
	lazy val completions = a.completions.filterS(s => apply(b)(s).resultEmpty.isDefined )
	lazy val resultEmpty = if(b.resultEmpty.isDefined) a.resultEmpty else None
}

private final class Not(delegate: Parser[_]) extends ValidParser[Unit]
{
	def derive(c: Char) = if(delegate.valid) not(delegate derive c) else this
	def completions = Completions.empty
	lazy val resultEmpty = if(delegate.resultEmpty.isDefined) None else Some(())
}
private final class Examples[T](delegate: Parser[T], fixed: Set[String]) extends ValidParser[T]
{
	def derive(c: Char) = examples(delegate derive c, fixed.collect { case x if x.length > 0 && x(0) == c => x substring 1 })
	lazy val resultEmpty = delegate.resultEmpty
	lazy val completions =
		if(fixed.isEmpty)
			if(resultEmpty.isEmpty) Completions.nil else Completions.empty
		else
			Completions(fixed map(f => Completion.suggestion(f)) )
	override def toString = "examples(" + delegate + ", " + fixed.take(2) + ")"
}
private final class StringLiteral(str: String, remaining: List[Char]) extends ValidParser[String]
{
	assert(str.length > 0 && !remaining.isEmpty)
	def resultEmpty = None
	def derive(c: Char) = if(remaining.head == c) stringLiteral(str, remaining.tail) else Invalid
	lazy val completions = Completions.single(Completion.suggestion(remaining.mkString))
	override def toString = '"' + str + '"'
}
private final class CharacterClass(f: Char => Boolean) extends ValidParser[Char]
{
	def resultEmpty = None
	def derive(c: Char) = if( f(c) ) successStrict(c) else Invalid
	def completions = Completions.empty
	override def toString = "class()"
}
private final class Optional[T](delegate: Parser[T]) extends ValidParser[Option[T]]
{
	def resultEmpty = Some(None)
	def derive(c: Char) = (delegate derive c).map(Some(_))
	lazy val completions = Completion.empty +: delegate.completions
	override def toString = delegate.toString + "?"
}
private final class Repeat[T](partial: Option[Parser[T]], repeated: Parser[T], min: Int, max: UpperBound, accumulatedReverse: List[T]) extends ValidParser[Seq[T]]
{
	assume(0 <= min, "Minimum occurences must be non-negative")
	assume(max >= min, "Minimum occurences must be less than the maximum occurences")

	def derive(c: Char) =
		partial match
		{
			case Some(part) =>
				val partD = repeat(Some(part derive c), repeated, min, max, accumulatedReverse)
				part.resultEmpty match
				{
					case Some(pv) => partD | repeatDerive(c, pv :: accumulatedReverse)
					case None => partD
				}
			case None => repeatDerive(c, accumulatedReverse)
		}

	def repeatDerive(c: Char, accRev: List[T]): Parser[Seq[T]]  =  repeat(Some(repeated derive c), repeated, (min - 1) max 0, max.decrement, accRev)

	lazy val completions =
	{
		def pow(comp: Completions, exp: Completions, n: Int): Completions =
			if(n == 1) comp else pow(comp x exp, exp, n - 1)

		val repC = repeated.completions
		val fin = if(min == 0) Completion.empty +: repC else pow(repC, repC, min)
		partial match
		{
			case Some(p) => p.completions x fin
			case None => fin
		}
	}
	lazy val resultEmpty: Option[Seq[T]] =
	{
		val partialAccumulatedOption =
			partial match
			{
				case None => Some(accumulatedReverse)
				case Some(partialPattern) => partialPattern.resultEmpty.map(_ :: accumulatedReverse)
			}
		for(partialAccumulated <- partialAccumulatedOption; repeatEmpty <- repeatedParseEmpty) yield
			partialAccumulated reverse_::: repeatEmpty
	}
	private def repeatedParseEmpty: Option[List[T]] =
	{
		if(min == 0)
			Some(Nil)
		else
			// forced determinism
			for(value <- repeated.resultEmpty) yield
				List.make(min, value)
	}
	override def toString = "repeat(" + min + "," + max +"," + partial + "," + repeated + ")"
}