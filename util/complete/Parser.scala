/* sbt -- Simple Build Tool
 * Copyright 2008, 2010, 2011  Mark Harrah
 */
package sbt.complete

	import Parser._
	import sbt.Types.{left, right, some}
	import sbt.Collections.separate

sealed trait Parser[+T]
{
	def derive(i: Char): Parser[T]
	def resultEmpty: Result[T]
	def result: Option[T]
	def completions: Completions
	def failure: Option[Failure]
	def isTokenStart = false
	def ifValid[S](p: => Parser[S]): Parser[S]
	def valid: Boolean
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
	/** Produces a Parser that applies either the original Parser or `b`.*/
	def |[B >: A](b: Parser[B]): Parser[B]
	/** Produces a Parser that applies either the original Parser or `b`.*/
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

	/** Uses the specified message if the original Parser fails.*/
	def !!!(msg: String): Parser[A]
	
	def unary_- : Parser[Unit]
	def & (o: Parser[_]): Parser[A]
	def - (o: Parser[_]): Parser[A]
	/** Explicitly defines the completions for the original Parser.*/
	def examples(s: String*): Parser[A]
	/** Explicitly defines the completions for the original Parser.*/
	def examples(s: Set[String], check: Boolean = false): Parser[A]
	/** Converts a Parser returning a Char sequence to a Parser returning a String.*/
	def string(implicit ev: A <:< Seq[Char]): Parser[String]
	/** Produces a Parser that filters the original parser.
	* If 'f' is not true when applied to the output of the original parser, the Parser returned by this method fails.*/
	def filter(f: A => Boolean, msg: String => String): Parser[A]

	def flatMap[B](f: A => Parser[B]): Parser[B]
}
object Parser extends ParserMain
{
	sealed abstract class Result[+T] {
		def isFailure: Boolean
		def isValid: Boolean
		def errors: Seq[String]
		def or[B >: T](b: => Result[B]): Result[B]
		def either[B](b: => Result[B]): Result[Either[T,B]]
		def map[B](f: T => B): Result[B]
		def flatMap[B](f: T => Result[B]): Result[B]
		def &&(b: => Result[_]): Result[T]
		def filter(f: T => Boolean, msg: => String): Result[T]
		def seq[B](b: => Result[B]): Result[(T,B)] = app(b)( (m,n) => (m,n) )
		def app[B,C](b: => Result[B])(f: (T, B) => C): Result[C]
		def toEither: Either[Seq[String], T]
	}
	final case class Value[+T](value: T) extends Result[T] {
		def isFailure = false
		def isValid: Boolean = true
		def errors = Nil
		def app[B,C](b: => Result[B])(f: (T, B) => C): Result[C] = b match {
			case fail: Failure => fail
			case Value(bv) => Value(f(value, bv))
		}
		def &&(b: => Result[_]): Result[T] = b match { case f: Failure => f; case _ => this }
		def or[B >: T](b: => Result[B]): Result[B] = this
		def either[B](b: => Result[B]): Result[Either[T,B]] = Value(Left(value))
		def map[B](f: T => B): Result[B] = Value(f(value))
		def flatMap[B](f: T => Result[B]): Result[B] = f(value)
		def filter(f: T => Boolean, msg: => String): Result[T] = if(f(value)) this else mkFailure(msg)
		def toEither = Right(value)
	}
	final class Failure(mkErrors: => Seq[String]) extends Result[Nothing] {
		lazy val errors: Seq[String] = mkErrors
		def isFailure = true
		def isValid = false
		def map[B](f: Nothing => B) = this
		def flatMap[B](f: Nothing => Result[B]) = this
		def or[B](b: => Result[B]): Result[B] = b match {
			case v: Value[B] => v
			case f: Failure => concatErrors(f)
		}
		def either[B](b: => Result[B]): Result[Either[Nothing,B]] = b match {
			case Value(v) => Value(Right(v))
			case f: Failure => concatErrors(f)
		}
		def filter(f: Nothing => Boolean, msg: => String) = this
		def app[B,C](b: => Result[B])(f: (Nothing, B) => C): Result[C] = this
		def &&(b: => Result[_]) = this
		def toEither = Left(errors)

		private[this] def concatErrors(f: Failure) = mkFailures(errors ++ f.errors)
	}
	def mkFailures(errors: => Seq[String]): Failure = new Failure(errors.distinct)
	def mkFailure(error: => String): Failure = new Failure(error :: Nil)

	def checkMatches(a: Parser[_], completions: Seq[String])
	{
		val bad = completions.filter( apply(a)(_).resultEmpty.isFailure)
		if(!bad.isEmpty) error("Invalid example completions: " + bad.mkString("'", "', '", "'"))
	}
	def tuple[A,B](a: Option[A], b: Option[B]): Option[(A,B)] =
		(a,b) match { case (Some(av), Some(bv)) => Some(av, bv); case _ => None }

	def mapParser[A,B](a: Parser[A], f: A => B): Parser[B] =
		a.ifValid {
			a.result match
			{
				case Some(av) => success( f(av) )
				case None => new MapParser(a, f)
			}
		}

	def bindParser[A,B](a: Parser[A], f: A => Parser[B]): Parser[B] =
		a.ifValid {
			a.result match
			{
				case Some(av) => f(av)
				case None => new BindParser(a, f)
			}
		}

	def filterParser[T](a: Parser[T], f: T => Boolean, seen: String, msg: String => String): Parser[T] =
		a.ifValid {
			a.result match
			{
				case Some(av) => if( f(av) ) success( av ) else Parser.failure(msg(seen))
				case None => new Filter(a, f, seen, msg)
			}
		}

	def seqParser[A,B](a: Parser[A], b: Parser[B]): Parser[(A,B)] =
		a.ifValid { b.ifValid {
			(a.result, b.result) match {
				case (Some(av), Some(bv)) => success( (av, bv) )
				case (Some(av), None) => b map { bv => (av, bv) }
				case (None, Some(bv)) => a map { av => (av, bv) }
				case (None, None) => new SeqParser(a,b)
			}
		}}

	def choiceParser[A,B](a: Parser[A], b: Parser[B]): Parser[Either[A,B]] =
		if(a.valid)
			if(b.valid) new HetParser(a,b) else a.map( left.fn )
		else
			b.map( right.fn )

	def opt[T](a: Parser[T]): Parser[Option[T]] =
		if(a.valid) new Optional(a) else success(None)

	def onFailure[T](delegate: Parser[T], msg: String): Parser[T] =
		if(delegate.valid) new OnFailure(delegate, msg) else failure(msg)

	def zeroOrMore[T](p: Parser[T]): Parser[Seq[T]] = repeat(p, 0, Infinite)
	def oneOrMore[T](p: Parser[T]): Parser[Seq[T]] = repeat(p, 1, Infinite)

	def repeat[T](p: Parser[T], min: Int = 0, max: UpperBound = Infinite): Parser[Seq[T]] =
		repeat(None, p, min, max, Nil)
	private[complete] def repeat[T](partial: Option[Parser[T]], repeated: Parser[T], min: Int, max: UpperBound, revAcc: List[T]): Parser[Seq[T]] =
	{
		assume(min >= 0, "Minimum must be greater than or equal to zero (was " + min + ")")
		assume(max >= min, "Minimum must be less than or equal to maximum (min: " + min + ", max: " + max + ")")

		def checkRepeated(invalidButOptional: => Parser[Seq[T]]): Parser[Seq[T]] =
			repeated match
			{
				case i: Invalid if min == 0 => invalidButOptional
				case i: Invalid => i
				case _ =>
					repeated.result match
					{
						case Some(value) => success(revAcc reverse_::: value :: Nil) // revAcc should be Nil here
						case None => if(max.isZero) success(revAcc.reverse) else new Repeat(partial, repeated, min, max, revAcc)
					}
			}
		
		partial match
		{
			case Some(part) =>
				part.ifValid {
					part.result match
					{
						case Some(value) => repeat(None, repeated, min, max, value :: revAcc)
						case None => checkRepeated(part.map(lv => (lv :: revAcc).reverse))
					}
				}
			case None => checkRepeated(success(Nil))
		}
	}

	def sub[T](a: Parser[T], b: Parser[_]): Parser[T]  =  and(a, not(b))

	def and[T](a: Parser[T], b: Parser[_]): Parser[T]  =  a.ifValid( b.ifValid( new And(a, b) ))
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
		def !!!(msg: String): Parser[A] = onFailure(a, msg)
	
		def unary_- = not(a)
		def & (o: Parser[_]) = and(a, o)
		def - (o: Parser[_]) = sub(a, o)
		def examples(s: String*): Parser[A] = examples(s.toSet)
		def examples(s: Set[String], check: Boolean = false): Parser[A] = Parser.examples(a, s, check)
		def filter(f: A => Boolean, msg: String => String): Parser[A] = filterParser(a, f, "", msg)
		def string(implicit ev: A <:< Seq[Char]): Parser[String] = map(_.mkString)
		def flatMap[B](f: A => Parser[B]) = bindParser(a, f)
	}
	implicit def literalRichParser(c: Char): RichParser[Char] = richParser(c)
	implicit def literalRichParser(s: String): RichParser[String] = richParser(s)

	def invalid(msgs: => Seq[String]): Parser[Nothing] = Invalid(mkFailures(msgs))
	def failure(msg: => String): Parser[Nothing] = invalid(msg :: Nil)
	def success[T](value: T): Parser[T] = new ValidParser[T] {
		override def result = Some(value)
		def resultEmpty = Value(value)
		def derive(c: Char) = Parser.failure("Expected end of input.")
		def completions = Completions.empty
		override def toString = "success(" + value + ")"
	}

	implicit def range(r: collection.immutable.NumericRange[Char]): Parser[Char] =
		charClass(r contains _).examples(r.map(_.toString) : _*)
	def chars(legal: String): Parser[Char] =
	{
		val set = legal.toSet
		charClass(set, "character in '" + legal + "'") examples(set.map(_.toString))
	}
	def charClass(f: Char => Boolean, label: String = "<unspecified>"): Parser[Char] = new CharacterClass(f, label)

	implicit def literal(ch: Char): Parser[Char] = new ValidParser[Char] {
		def result = None
		def resultEmpty = mkFailure( "Expected '" + ch + "'" )
		def derive(c: Char) = if(c == ch) success(ch) else new Invalid(resultEmpty)
		def completions = Completions.single(Completion.suggestStrict(ch.toString))
		override def toString = "'" + ch + "'"
	}
	implicit def literal(s: String): Parser[String] = stringLiteral(s, 0)
	object ~ {
		def unapply[A,B](t: (A,B)): Some[(A,B)] = Some(t)
	}

	// intended to be temporary pending proper error feedback
	def result[T](p: Parser[T], s: String): Either[(Seq[String],Int), T] =
	{
		def loop(i: Int, a: Parser[T]): Either[(Seq[String],Int), T] =
			a match
			{
				case Invalid(f) => Left( (f.errors, i) )
				case _ =>
					val ci = i+1
					if(ci >= s.length)
						a.resultEmpty.toEither.left.map { msgs =>
							val nonEmpty = if(msgs.isEmpty) "Unexpected end of input" :: Nil else msgs
							(nonEmpty, ci)
						}
					else
						loop(ci, a derive s(ci) )
			}
		loop(-1, p)
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
				case Some(av) => success( av )
				case None =>
					if(check) checkMatches(a, completions.toSeq)
					new Examples(a, completions)
			}
		}
		else a

	def matched(t: Parser[_], seen: Vector[Char] = Vector.empty, partial: Boolean = false): Parser[String] =
		t match
		{
			case i: Invalid => if(partial && !seen.isEmpty) success(seen.mkString) else i
			case _ =>
				if(t.result.isEmpty)
					new MatchedString(t, seen, partial)
				else
					success(seen.mkString)
		}

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

	def seq[T](p: Seq[Parser[T]]): Parser[Seq[T]] = seq0(p, Nil)
	def seq0[T](p: Seq[Parser[T]], errors: => Seq[String]): Parser[Seq[T]] =
	{
		val (newErrors, valid) = separate(p) { case Invalid(f) => Left(f.errors); case ok => Right(ok) }
		def combinedErrors = errors ++ newErrors.flatten
		if(valid.isEmpty) invalid(combinedErrors) else new ParserSeq(valid, combinedErrors)
	}

	def stringLiteral(s: String, start: Int): Parser[String] =
	{
		val len = s.length
		if(len == 0) error("String literal cannot be empty") else if(start >= len) success(s) else new StringLiteral(s, start)
	}
}
sealed trait ValidParser[T] extends Parser[T]
{
	final def valid = true
	final def failure = None
	final def ifValid[S](p: => Parser[S]): Parser[S] = p
}
private final case class Invalid(fail: Failure) extends Parser[Nothing]
{
	def failure = Some(fail)
	def result = None
	def resultEmpty = fail
	def derive(c: Char) = error("Invalid.")
	def completions = Completions.nil
	override def toString = fail.errors.mkString("; ")
	def valid = false
	def ifValid[S](p: => Parser[S]): Parser[S] = this
}
private final class OnFailure[A](a: Parser[A], message: String) extends ValidParser[A]
{
	def result = a.result
	def resultEmpty = a.resultEmpty match { case f: Failure => mkFailure(message); case v: Value[A] => v }
	def derive(c: Char) = onFailure(a derive c, message)
	def completions = a.completions
	override def toString = "(" + a + " !!! \"" + message + "\" )"
	override def isTokenStart = a.isTokenStart
}
private final class SeqParser[A,B](a: Parser[A], b: Parser[B]) extends ValidParser[(A,B)]
{
	lazy val result = tuple(a.result,b.result)
	lazy val resultEmpty = a.resultEmpty seq b.resultEmpty
	def derive(c: Char) =
	{
		val common = a.derive(c) ~ b
		a.resultEmpty match
		{
			case Value(av) => common | b.derive(c).map(br => (av,br))
			case _: Failure => common
		}
	}
	lazy val completions = a.completions x b.completions
	override def toString = "(" + a + " ~ " + b + ")"
}

private final class HomParser[A](a: Parser[A], b: Parser[A]) extends ValidParser[A]
{
	lazy val result = tuple(a.result, b.result) map (_._1)
	def derive(c: Char) = (a derive c) | (b derive c)
	lazy val resultEmpty = a.resultEmpty or b.resultEmpty
	lazy val completions = a.completions ++ b.completions
	override def toString = "(" + a + " | " + b + ")"
}
private final class HetParser[A,B](a: Parser[A], b: Parser[B]) extends ValidParser[Either[A,B]]
{
	lazy val result = tuple(a.result, b.result) map { case (a,b) => Left(a) }
	def derive(c: Char) = (a derive c) || (b derive c)
	lazy val resultEmpty = a.resultEmpty either b.resultEmpty
	lazy val completions = a.completions ++ b.completions
	override def toString = "(" + a + " || " + b + ")"
}
private final class ParserSeq[T](a: Seq[Parser[T]], errors: => Seq[String]) extends ValidParser[Seq[T]]
{
	assert(!a.isEmpty)
	lazy val resultEmpty: Result[Seq[T]] =
	{
		val res = a.map(_.resultEmpty)
		val (failures, values) = separate(res)(_.toEither)
		if(failures.isEmpty) Value(values) else mkFailures(failures.flatten ++ errors)
	}
	def result = {
		val success = a.flatMap(_.result)
		if(success.length == a.length) Some(success) else None
	}
	lazy val completions = a.map(_.completions).reduceLeft(_ ++ _)
	def derive(c: Char) = seq0(a.map(_ derive c), errors)
	override def toString = "seq(" + a + ")"
}

private final class BindParser[A,B](a: Parser[A], f: A => Parser[B]) extends ValidParser[B]
{
	lazy val result = a.result flatMap { av => f(av).result }
	lazy val resultEmpty = a.resultEmpty flatMap { av => f(av).resultEmpty }
	lazy val completions =
		a.completions flatMap { c =>
			apply(a)(c.append).resultEmpty match {
				case _: Failure => Completions.strict(Set.empty + c)
				case Value(av) => c x f(av).completions
			}
		}

	def derive(c: Char) =
	{
		val common = a derive c flatMap f
		a.resultEmpty match
		{
			case Value(av) => common | derive1(f(av), c)
			case _: Failure => common
		}
	}
	override def isTokenStart = a.isTokenStart
	override def toString = "bind(" + a + ")"
}
private final class MapParser[A,B](a: Parser[A], f: A => B) extends ValidParser[B]
{
	lazy val result = a.result map f
	lazy val resultEmpty = a.resultEmpty map f
	def derive(c: Char) = (a derive c) map f
	def completions = a.completions
	override def isTokenStart = a.isTokenStart
	override def toString = "map(" + a + ")"
}
private final class Filter[T](p: Parser[T], f: T => Boolean, seen: String, msg: String => String) extends ValidParser[T]
{
	def filterResult(r: Result[T]) = p.resultEmpty.filter(f, msg(seen))
	lazy val result = p.result filter f
	lazy val resultEmpty = filterResult(p.resultEmpty)
	def derive(c: Char) = filterParser(p derive c, f, seen + c, msg)
	lazy val completions = p.completions filterS { s => filterResult(apply(p)(s).resultEmpty).isValid }
	override def toString = "filter(" + p + ")"
	override def isTokenStart = p.isTokenStart
}
private final class MatchedString(delegate: Parser[_], seenV: Vector[Char], partial: Boolean) extends ValidParser[String]
{
	lazy val seen = seenV.mkString
	def derive(c: Char) = matched(delegate derive c, seenV :+ c, partial)
	def completions = delegate.completions
	def result = if(delegate.result.isDefined) Some(seen) else None
	def resultEmpty = delegate.resultEmpty match { case f: Failure if !partial => f; case _ => Value(seen) }
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

	def result = delegate.result
	def resultEmpty = delegate.resultEmpty
	override def isTokenStart = true
	override def toString = "token('" + seen + "', " + track + ", " + delegate + ")"
}
private final class And[T](a: Parser[T], b: Parser[_]) extends ValidParser[T]
{
	lazy val result = tuple(a.result,b.result) map { _._1 }
	def derive(c: Char) = (a derive c) & (b derive c)
	lazy val completions = a.completions.filterS(s => apply(b)(s).resultEmpty.isValid )
	lazy val resultEmpty = a.resultEmpty && b.resultEmpty
}

private final class Not(delegate: Parser[_]) extends ValidParser[Unit]
{
	def derive(c: Char) = if(delegate.valid) not(delegate derive c) else this
	def completions = Completions.empty
	def result = None
	lazy val resultEmpty = delegate.resultEmpty match {
		case f: Failure => Value(())
		case v: Value[_] => mkFailure("Excluded.")
	}
}
private final class Examples[T](delegate: Parser[T], fixed: Set[String]) extends ValidParser[T]
{
	def derive(c: Char) = examples(delegate derive c, fixed.collect { case x if x.length > 0 && x(0) == c => x substring 1 })
	def result = delegate.result
	lazy val resultEmpty = delegate.resultEmpty
	lazy val completions =
		if(fixed.isEmpty)
			if(resultEmpty.isValid) Completions.nil else Completions.empty
		else
			Completions(fixed map(f => Completion.suggestion(f)) )
	override def toString = "examples(" + delegate + ", " + fixed.take(2) + ")"
}
private final class StringLiteral(str: String, start: Int) extends ValidParser[String]
{
	assert(0 <= start && start < str.length)
	def failMsg = "Expected '" + str + "'"
	def resultEmpty = mkFailure(failMsg)
	def result = None
	def derive(c: Char) = if(str.charAt(start) == c) stringLiteral(str, start+1) else new Invalid(resultEmpty)
	lazy val completions = Completions.single(Completion.suggestion(str.substring(start)))
	override def toString = '"' + str + '"'
}
private final class CharacterClass(f: Char => Boolean, label: String) extends ValidParser[Char]
{
	def result = None
	def resultEmpty = mkFailure("Expected " + label)
	def derive(c: Char) = if( f(c) ) success(c) else Invalid(resultEmpty)
	def completions = Completions.empty
	override def toString = "class(" + label + ")"
}
private final class Optional[T](delegate: Parser[T]) extends ValidParser[Option[T]]
{
	def result = delegate.result map some.fn
	def resultEmpty = Value(None)
	def derive(c: Char) = (delegate derive c).map(some.fn)
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
					case Value(pv) => partD | repeatDerive(c, pv :: accumulatedReverse)
					case _: Failure => partD
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
	def result = None
	lazy val resultEmpty: Result[Seq[T]] =
	{
		val partialAccumulatedOption =
			partial match
			{
				case None => Value(accumulatedReverse)
				case Some(partialPattern) => partialPattern.resultEmpty.map(_ :: accumulatedReverse)
			}
		(partialAccumulatedOption app repeatedParseEmpty)(_ reverse_::: _)
	}
	private def repeatedParseEmpty: Result[List[T]] =
	{
		if(min == 0)
			Value(Nil)
		else
			// forced determinism
			for(value <- repeated.resultEmpty) yield
				List.make(min, value)
	}
	override def toString = "repeat(" + min + "," + max +"," + partial + "," + repeated + ")"
}