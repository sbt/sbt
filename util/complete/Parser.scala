/* sbt -- Simple Build Tool
 * Copyright 2008, 2010  Mark Harrah
 */
package sbt.parse

	import Parser._

sealed trait Parser[+T]
{
	def derive(i: Char): Parser[T]
	def resultEmpty: Option[T]
	def result: Option[T] = None
	def completions: Completions
	def valid: Boolean = true
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
	def ||[B >: A](b: Parser[B]): Parser[B]
	/** Produces a Parser that applies either the original Parser or `next`.*/
	def |[B](b: Parser[B]): Parser[Either[A,B]]
	/** Produces a Parser that applies the original Parser to the input and then applies `f` to the result.*/
	def map[B](f: A => B): Parser[B]
	/** Returns the original parser.  This is useful for converting literals to Parsers.
	* For example, `'c'.id` or `"asdf".id`*/
	def id: Parser[A]
}
object Parser
{
	def apply[T](p: Parser[T])(s: String): Parser[T] =
		(p /: s)(derive1)

	def derive1[T](p: Parser[T], c: Char): Parser[T] =
		p.derive(c)

	def completions(p: Parser[_], s: String): Completions = completions( apply(p)(s) )
	def completions(p: Parser[_]): Completions = Completions.mark x p.completions

	implicit def richParser[A](a: Parser[A]): RichParser[A] = new RichParser[A]
	{
		def ~[B](b: Parser[B]) = seqParser(a, b)
		def |[B](b: Parser[B]) = choiceParser(a,b)
		def ||[B >: A](b: Parser[B]) = homParser(a,b)
		def ? = opt(a)
		def * = zeroOrMore(a)
		def + = oneOrMore(a)
		def map[B](f: A => B) = mapParser(a, f)
		def id = a
	}
	implicit def literalRichParser(c: Char): RichParser[Char] = richParser(c)
	implicit def literalRichParser(s: String): RichParser[String] = richParser(s)
	def examples[A](a: Parser[A], completions: Set[String]): Parser[A] =
		if(a.valid) {
			a.result match
			{
				case Some(av) => success( av )
				case None => new Examples(a, completions)
			}
		}
		else Invalid

	def mapParser[A,B](a: Parser[A], f: A => B): Parser[B] =
		if(a.valid) {
			a.result match
			{
				case Some(av) => success( f(av) )
				case None => new MapParser(a, f)
			}
		}
		else Invalid

	def seqParser[A,B](a: Parser[A], b: Parser[B]): Parser[(A,B)] =
		if(a.valid && b.valid) {
			(a.result, b.result) match {
				case (Some(av), Some(bv)) => success( (av, bv) )
				case (Some(av), None) => b map { bv => (av, bv) }
				case (None, Some(bv)) => a map { av => (av, bv) }
				case (None, None) => new SeqParser(a,b)
			}
		}
		else Invalid

	def token[T](t: Parser[T]): Parser[T] = tokenStart(t, "")
	def tokenStart[T](t: Parser[T], seen: String): Parser[T] =
		if(t.valid && !t.isTokenStart)
		{
			t.result match
			{
				case None => new TokenStart(t, seen)
				case Some(tv) => success(tv)
			}
		}
		else
			t

	def homParser[A](a: Parser[A], b: Parser[A]): Parser[A] =
		if(a.valid) {
			if(b.valid) {
				(a.result orElse b.result) match
				{
					case Some(v) => success( v )
					case None => new HomParser(a, b)
				}
			}
			else a
		}
		else b

	def choiceParser[A,B](a: Parser[A], b: Parser[B]): Parser[Either[A,B]] =
		if(a.valid) {
			if(b.valid) {
				a.result match
				{
					case Some(av) => success( Left(av) )
					case None =>
						b.result match
						{
							case Some(bv) => success( Right(bv) )
							case None => new HetParser(a, b)
						}
				}
			}
			else
				a.map( Left(_) )
		}
		else
			b.map( Right(_) )
			
	def opt[T](a: Parser[T]): Parser[Option[T]] =
		if(a.valid) {
			a.result match
			{
				case None => new Optional(a)
				case x => success(x)
			}
		}
		else success(None)

	def zeroOrMore[T](p: Parser[T]): Parser[Seq[T]] = repeat(p, 0, Infinite)
	def oneOrMore[T](p: Parser[T]): Parser[Seq[T]] = repeat(p, 1, Infinite)

	def repeat[T](p: Parser[T], min: Int = 0, max: UpperBound = Infinite): Parser[Seq[T]] =
		repeat(None, p, min, max, Nil)
	private[parse] def repeat[T](partial: Option[Parser[T]], repeated: Parser[T], min: Int, max: UpperBound, revAcc: List[T]): Parser[Seq[T]] =
	{
		assume(min >= 0, "Minimum must be greater than or equal to zero")
		
		def checkRepeated(invalidButOptional: => Parser[Seq[T]]): Parser[Seq[T]] =
			if(repeated.valid)
				repeated.result match
				{
					case Some(value) => success(value :: Nil)
					case None => new Repeat(partial, repeated, min, max, revAcc)
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
			case None => checkRepeated(success(Nil))
		}
	}

	def success[T](value: T): Parser[T] = new Parser[T] {
		override def result = Some(value)
		def resultEmpty = result
		def derive(c: Char) = Invalid
		def completions = Completions.empty
	}

	def charClass(f: Char => Boolean): Parser[Char] = new CharacterClass(f)
	implicit def literal(ch: Char): Parser[Char] = new Parser[Char] {
		def resultEmpty = None
		def derive(c: Char) = if(c == ch) success(ch) else Invalid
		def completions = Completions.single(ch.toString)
	}
	implicit def literal(s: String): Parser[String] = stringLiteral(s, s.toList)
	def stringLiteral(s: String, remaining: List[Char]): Parser[String] =
		if(remaining.isEmpty) success(s) else if(s.isEmpty) error("String literal cannot be empty") else new StringLiteral(s, remaining)
}
private final object Invalid extends Parser[Nothing]
{
	def resultEmpty = None
	def derive(c: Char) = error("Invalid.")
	override def valid = false
	def completions = Completions.empty
}
private final class SeqParser[A,B](a: Parser[A], b: Parser[B]) extends Parser[(A,B)]
{
	def cross(ao: Option[A], bo: Option[B]): Option[(A,B)] = for(av <- ao; bv <- bo) yield (av,bv)
	lazy val resultEmpty = cross(a.resultEmpty, b.resultEmpty)
	def derive(c: Char) =
	{
		val common = a.derive(c) ~ b
		a.resultEmpty match
		{
			case Some(av) => common || b.derive(c).map(br => (av,br))
			case None => common
		}
	}
	lazy val completions = a.completions x b.completions
}

private final class HomParser[A](a: Parser[A], b: Parser[A]) extends Parser[A]
{
	def derive(c: Char) = (a derive c) || (b derive c)
	lazy val resultEmpty = a.resultEmpty orElse b.resultEmpty
	lazy val completions = a.completions ++ b.completions
}
private final class HetParser[A,B](a: Parser[A], b: Parser[B]) extends Parser[Either[A,B]]
{
	def derive(c: Char) = (a derive c) | (b derive c)
	lazy val resultEmpty = a.resultEmpty.map(Left(_)) orElse b.resultEmpty.map(Right(_))
	lazy val completions = a.completions ++ b.completions
}
private final class MapParser[A,B](a: Parser[A], f: A => B) extends Parser[B]
{
	lazy val resultEmpty = a.resultEmpty map f
	def derive(c: Char) = (a derive c) map f
	def completions = a.completions
	override def isTokenStart = a.isTokenStart
}
private final class TokenStart[T](delegate: Parser[T], seen: String) extends Parser[T]
{
	def derive(c: Char) = tokenStart( delegate derive c, seen + c )
	lazy val completions =
	{
		val dcs = delegate.completions
		Completions( for(c <- dcs.get) yield Completion(seen, c.append, true) )
	}
	def resultEmpty = delegate.resultEmpty
	override def isTokenStart = true
}
private final class Examples[T](delegate: Parser[T], fixed: Set[String]) extends Parser[T]
{
	def derive(c: Char) = examples(delegate.derive(c), fixed.collect { case x if x.length > 0 && x(0) == c => x.tail })
	def resultEmpty = delegate.resultEmpty
	lazy val completions = Completions(fixed map { ex => Completion.strict("",ex,false) } )
}
private final class StringLiteral(str: String, remaining: List[Char]) extends Parser[String]
{
	assert(str.length > 0 && !remaining.isEmpty)
	def resultEmpty = None
	def derive(c: Char) = if(remaining.head == c) stringLiteral(str, remaining.tail) else Invalid
	lazy val completions = Completions.single(remaining.mkString)
}
private final class CharacterClass(f: Char => Boolean) extends Parser[Char]
{
	def resultEmpty = None
	def derive(c: Char) = if( f(c) ) success(c) else Invalid
	def completions = Completions.empty
}
private final class Optional[T](delegate: Parser[T]) extends Parser[Option[T]]
{
	def resultEmpty = Some(None)
	def derive(c: Char) = (delegate derive c).map(Some(_))
	lazy val completions = Completion.empty +: delegate.completions
}
private final class Repeat[T](partial: Option[Parser[T]], repeated: Parser[T], min: Int, max: UpperBound, accumulatedReverse: List[T]) extends Parser[Seq[T]]
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
					case Some(pv) => partD || repeatDerive(c, pv :: accumulatedReverse)
					case None => partD
				}
			case None => repeatDerive(c, accumulatedReverse)
		}

	def repeatDerive(c: Char, accRev: List[T]): Parser[Seq[T]]  =  repeat(Some(repeated derive c), repeated, (min - 1) max 0, max.decrement, accRev)

	lazy val completions =
	{
		val repC = repeated.completions
		val fin = if(min == 0) Completion.empty +: repC else repC
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
}