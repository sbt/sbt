/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package complete

import Parser._
import sbt.internal.util.Types.{ left, right, some }
import sbt.internal.util.Util.{ makeList, separate }

/**
 * A String parser that provides semi-automatic tab completion.
 * A successful parse results in a value of type `T`.
 * The methods in this trait are what must be implemented to define a new Parser implementation, but are not typically useful for common usage.
 * Instead, most useful methods for combining smaller parsers into larger parsers are implicitly added by the [[RichParser]] type.
 */
sealed trait Parser[+T] {
  def derive(i: Char): Parser[T]
  def resultEmpty: Result[T]
  def result: Option[T]
  def completions(level: Int): Completions
  def failure: Option[Failure]
  def isTokenStart = false
  def ifValid[S](p: => Parser[S]): Parser[S]
  def valid: Boolean
}

sealed trait RichParser[A] {

  /** Apply the original Parser and then apply `next` (in order).  The result of both is provides as a pair. */
  def ~[B](next: Parser[B]): Parser[(A, B)]

  /** Apply the original Parser one or more times and provide the non-empty sequence of results.*/
  def + : Parser[Seq[A]]

  /** Apply the original Parser zero or more times and provide the (potentially empty) sequence of results.*/
  def * : Parser[Seq[A]]

  /** Apply the original Parser zero or one times, returning None if it was applied zero times or the result wrapped in Some if it was applied once.*/
  def ? : Parser[Option[A]]

  /** Apply either the original Parser or `b`.*/
  def |[B >: A](b: Parser[B]): Parser[B]

  /** Apply either the original Parser or `b`.*/
  def ||[B](b: Parser[B]): Parser[Either[A, B]]

  /** Apply the original Parser to the input and then apply `f` to the result.*/
  def map[B](f: A => B): Parser[B]

  /**
   * Returns the original parser.  This is useful for converting literals to Parsers.
   * For example, `'c'.id` or `"asdf".id`
   */
  def id: Parser[A]

  /** Apply the original Parser, but provide `value` as the result if it succeeds. */
  def ^^^[B](value: B): Parser[B]

  /** Apply the original Parser, but provide `alt` as the result if it fails.*/
  def ??[B >: A](alt: B): Parser[B]

  /**
   * Produces a Parser that applies the original Parser and then applies `next` (in order), discarding the result of `next`.
   * (The arrow point in the direction of the retained result.)
   */
  def <~[B](b: Parser[B]): Parser[A]

  /**
   * Produces a Parser that applies the original Parser and then applies `next` (in order), discarding the result of the original parser.
   * (The arrow point in the direction of the retained result.)
   */
  def ~>[B](b: Parser[B]): Parser[B]

  /** Uses the specified message if the original Parser fails.*/
  def !!!(msg: String): Parser[A]

  /**
   * If an exception is thrown by the original Parser,
   * capture it and fail locally instead of allowing the exception to propagate up and terminate parsing.
   */
  def failOnException: Parser[A]

  /**
   * Apply the original parser, but only succeed if `o` also succeeds.
   * Note that `o` does not need to consume the same amount of input to satisfy this condition.
   */
  def &(o: Parser[_]): Parser[A]

  /** Explicitly defines the completions for the original Parser.*/
  def examples(s: String*): Parser[A]

  /** Explicitly defines the completions for the original Parser.*/
  def examples(s: Set[String], check: Boolean = false): Parser[A]

  /**
   * @param exampleSource the source of examples when displaying completions to the user.
   * @param maxNumberOfExamples limits the number of examples that the source of examples should return. This can
   *                            prevent lengthy pauses and avoids bad interactive user experience.
   * @param removeInvalidExamples indicates whether completion examples should be checked for validity (against the
   *                              given parser). Invalid examples will be filtered out and only valid suggestions will
   *                              be displayed.
   * @return a new parser with a new source of completions.
   */
  def examples(
      exampleSource: ExampleSource,
      maxNumberOfExamples: Int,
      removeInvalidExamples: Boolean
  ): Parser[A]

  /**
   * @param exampleSource the source of examples when displaying completions to the user.
   * @return a new parser with a new source of completions. It displays at most 25 completion examples and does not
   *         remove invalid examples.
   */
  def examples(exampleSource: ExampleSource): Parser[A] =
    examples(exampleSource, maxNumberOfExamples = 25, removeInvalidExamples = false)

  /** Converts a Parser returning a Char sequence to a Parser returning a String.*/
  def string(implicit ev: A <:< Seq[Char]): Parser[String]

  /**
   * Produces a Parser that filters the original parser.
   * If 'f' is not true when applied to the output of the original parser, the Parser returned by this method fails.
   * The failure message is constructed by applying `msg` to the String that was successfully parsed by the original parser.
   */
  def filter(f: A => Boolean, msg: String => String): Parser[A]

  /** Applies the original parser, applies `f` to the result to get the next parser, and applies that parser and uses its result for the overall result. */
  def flatMap[B](f: A => Parser[B]): Parser[B]
}

/** Contains Parser implementation helper methods not typically needed for using parsers. */
object Parser extends ParserMain {
  sealed abstract class Result[+T] {
    def isFailure: Boolean
    def isValid: Boolean
    def errors: Seq[String]
    def or[B >: T](b: => Result[B]): Result[B]
    def either[B](b: => Result[B]): Result[Either[T, B]]
    def map[B](f: T => B): Result[B]
    def flatMap[B](f: T => Result[B]): Result[B]
    def &&(b: => Result[_]): Result[T]
    def filter(f: T => Boolean, msg: => String): Result[T]
    def seq[B](b: => Result[B]): Result[(T, B)] = app(b)((m, n) => (m, n))
    def app[B, C](b: => Result[B])(f: (T, B) => C): Result[C]
    def toEither: Either[() => Seq[String], T]
  }

  final case class Value[+T](value: T) extends Result[T] {
    def isFailure = false
    def isValid: Boolean = true
    def errors = Nil

    def app[B, C](b: => Result[B])(f: (T, B) => C): Result[C] = b match {
      case fail: Failure => fail
      case Value(bv)     => Value(f(value, bv))
    }

    def &&(b: => Result[_]): Result[T] = b match { case f: Failure => f; case _ => this }
    def or[B >: T](b: => Result[B]): Result[B] = this
    def either[B](b: => Result[B]): Result[Either[T, B]] = Value(Left(value))
    def map[B](f: T => B): Result[B] = Value(f(value))
    def flatMap[B](f: T => Result[B]): Result[B] = f(value)
    def filter(f: T => Boolean, msg: => String): Result[T] = if (f(value)) this else mkFailure(msg)
    def toEither = Right(value)
  }

  final class Failure private[sbt] (mkErrors: => Seq[String], val definitive: Boolean)
      extends Result[Nothing] {
    lazy val errors: Seq[String] = mkErrors
    def isFailure = true
    def isValid = false
    def map[B](f: Nothing => B) = this
    def flatMap[B](f: Nothing => Result[B]) = this

    def or[B](b: => Result[B]): Result[B] = b match {
      case v: Value[B] => v
      case f: Failure  => if (definitive) this else this ++ f
    }

    def either[B](b: => Result[B]): Result[Either[Nothing, B]] = b match {
      case Value(v)   => Value(Right(v))
      case f: Failure => if (definitive) this else this ++ f
    }

    def filter(f: Nothing => Boolean, msg: => String) = this
    def app[B, C](b: => Result[B])(f: (Nothing, B) => C): Result[C] = this
    def &&(b: => Result[_]) = this
    def toEither = Left(() => errors)

    private[sbt] def ++(f: Failure) = mkFailures(errors ++ f.errors)
  }

  def mkFailures(errors: => Seq[String], definitive: Boolean = false): Failure =
    new Failure(errors.distinct, definitive)

  def mkFailure(error: => String, definitive: Boolean = false): Failure =
    new Failure(error :: Nil, definitive)

  def tuple[A, B](a: Option[A], b: Option[B]): Option[(A, B)] =
    (a, b) match { case (Some(av), Some(bv)) => Some((av, bv)); case _ => None }

  def mapParser[A, B](a: Parser[A], f: A => B): Parser[B] =
    a.ifValid {
      a.result match {
        case Some(av) => success(f(av))
        case None     => new MapParser(a, f)
      }
    }

  def bindParser[A, B](a: Parser[A], f: A => Parser[B]): Parser[B] =
    a.ifValid {
      a.result match {
        case Some(av) => f(av)
        case None     => new BindParser(a, f)
      }
    }

  def filterParser[T](
      a: Parser[T],
      f: T => Boolean,
      seen: String,
      msg: String => String
  ): Parser[T] =
    a.ifValid {
      a.result match {
        case Some(av) if f(av) => success(av)
        case _                 => new Filter(a, f, seen, msg)
      }
    }

  def seqParser[A, B](a: Parser[A], b: Parser[B]): Parser[(A, B)] =
    a.ifValid {
      b.ifValid {
        (a.result, b.result) match {
          case (Some(av), Some(bv)) => success((av, bv))
          case (Some(av), None)     => b map (bv => (av, bv))
          case (None, Some(bv))     => a map (av => (av, bv))
          case (None, None)         => new SeqParser(a, b)
        }
      }
    }

  def choiceParser[A, B](a: Parser[A], b: Parser[B]): Parser[Either[A, B]] =
    if (a.valid)
      if (b.valid) new HetParser(a, b) else a.map(left.fn)
    else
      b.map(right.fn)

  def opt[T](a: Parser[T]): Parser[Option[T]] =
    if (a.valid) new Optional(a) else success(None)

  def onFailure[T](delegate: Parser[T], msg: String): Parser[T] =
    if (delegate.valid) new OnFailure(delegate, msg) else failure(msg)

  def trapAndFail[T](delegate: Parser[T]): Parser[T] =
    delegate.ifValid(new TrapAndFail(delegate))

  def zeroOrMore[T](p: Parser[T]): Parser[Seq[T]] = repeat(p, 0, Infinite)
  def oneOrMore[T](p: Parser[T]): Parser[Seq[T]] = repeat(p, 1, Infinite)

  def repeat[T](p: Parser[T], min: Int = 0, max: UpperBound = Infinite): Parser[Seq[T]] =
    repeat(None, p, min, max, Nil)

  private[complete] def repeat[T](
      partial: Option[Parser[T]],
      repeated: Parser[T],
      min: Int,
      max: UpperBound,
      revAcc: List[T]
  ): Parser[Seq[T]] = {
    assume(min >= 0, "Minimum must be greater than or equal to zero (was " + min + ")")
    assume(max >= min,
           "Minimum must be less than or equal to maximum (min: " + min + ", max: " + max + ")")

    def checkRepeated(invalidButOptional: => Parser[Seq[T]]): Parser[Seq[T]] =
      repeated match {
        case _: Invalid if min == 0 => invalidButOptional
        case i: Invalid             => i
        case _ =>
          repeated.result match {
            case Some(value) =>
              success(revAcc reverse_::: value :: Nil) // revAcc should be Nil here
            case None =>
              if (max.isZero) success(revAcc.reverse)
              else new Repeat(partial, repeated, min, max, revAcc)
          }
      }

    partial match {
      case Some(part) =>
        part.ifValid {
          part.result match {
            case Some(value) => repeat(None, repeated, min, max, value :: revAcc)
            case None        => checkRepeated(part.map(lv => (lv :: revAcc).reverse))
          }
        }
      case None => checkRepeated(success(Nil))
    }
  }

  def and[T](a: Parser[T], b: Parser[_]): Parser[T] = a.ifValid(b.ifValid(new And(a, b)))
}

trait ParserMain {

  /** Provides combinators for Parsers.*/
  implicit def richParser[A](a: Parser[A]): RichParser[A] = new RichParser[A] {
    def ~[B](b: Parser[B]) = seqParser(a, b)
    def ||[B](b: Parser[B]) = choiceParser(a, b)
    def |[B >: A](b: Parser[B]) = homParser(a, b)
    def ? = opt(a)
    def * = zeroOrMore(a)
    def + = oneOrMore(a)
    def map[B](f: A => B) = mapParser(a, f)
    def id = a

    def ^^^[B](value: B): Parser[B] = a map (_ => value)
    def ??[B >: A](alt: B): Parser[B] = a.? map { _ getOrElse alt }
    def <~[B](b: Parser[B]): Parser[A] = (a ~ b) map { case av ~ _ => av }
    def ~>[B](b: Parser[B]): Parser[B] = (a ~ b) map { case _ ~ bv => bv }
    def !!!(msg: String): Parser[A] = onFailure(a, msg)
    def failOnException: Parser[A] = trapAndFail(a)

    def &(o: Parser[_]) = and(a, o)
    def examples(s: String*): Parser[A] = examples(s.toSet)

    def examples(s: Set[String], check: Boolean = false): Parser[A] =
      examples(new FixedSetExamples(s), s.size, check)

    def examples(
        s: ExampleSource,
        maxNumberOfExamples: Int,
        removeInvalidExamples: Boolean
    ): Parser[A] =
      Parser.examples(a, s, maxNumberOfExamples, removeInvalidExamples)

    def filter(f: A => Boolean, msg: String => String): Parser[A] = filterParser(a, f, "", msg)
    def string(implicit ev: A <:< Seq[Char]): Parser[String] = map(_.mkString)
    def flatMap[B](f: A => Parser[B]) = bindParser(a, f)
  }

  implicit def literalRichCharParser(c: Char): RichParser[Char] = richParser(c)
  implicit def literalRichStringParser(s: String): RichParser[String] = richParser(s)

  /**
   * Construct a parser that is valid, but has no valid result.  This is used as a way
   * to provide a definitive Failure when a parser doesn't match empty input.  For example,
   * in `softFailure(...) | p`, if `p` doesn't match the empty sequence, the failure will come
   * from the Parser constructed by the `softFailure` method.
   */
  private[sbt] def softFailure(msg: => String, definitive: Boolean = false): Parser[Nothing] =
    SoftInvalid(mkFailures(msg :: Nil, definitive))

  /**
   * Defines a parser that always fails on any input with messages `msgs`.
   * If `definitive` is `true`, any failures by later alternatives are discarded.
   */
  def invalid(msgs: => Seq[String], definitive: Boolean = false): Parser[Nothing] =
    Invalid(mkFailures(msgs, definitive))

  /**
   * Defines a parser that always fails on any input with message `msg`.
   * If `definitive` is `true`, any failures by later alternatives are discarded.
   */
  def failure(msg: => String, definitive: Boolean = false): Parser[Nothing] =
    invalid(msg :: Nil, definitive)

  /** Defines a parser that always succeeds on empty input with the result `value`.*/
  def success[T](value: T): Parser[T] = new ValidParser[T] {
    override def result = Some(value)
    def resultEmpty = Value(value)
    def derive(c: Char) = Parser.failure("Expected end of input.")
    def completions(level: Int) = Completions.empty
    override def toString = "success(" + value + ")"
  }

  /** Presents a Char range as a Parser.  A single Char is parsed only if it is in the given range.*/
  implicit def range(r: collection.immutable.NumericRange[Char]): Parser[Char] =
    charClass(r contains _).examples(r.map(_.toString): _*)

  /** Defines a Parser that parses a single character only if it is contained in `legal`.*/
  def chars(legal: String): Parser[Char] = {
    val set = legal.toSet
    charClass(set, "character in '" + legal + "'") examples (set.map(_.toString))
  }

  /**
   * Defines a Parser that parses a single character only if the predicate `f` returns true for that character.
   * If this parser fails, `label` is used as the failure message.
   */
  def charClass(f: Char => Boolean, label: String = "<unspecified>"): Parser[Char] =
    new CharacterClass(f, label)

  /** Presents a single Char `ch` as a Parser that only parses that exact character. */
  implicit def literal(ch: Char): Parser[Char] = new ValidParser[Char] {
    def result = None
    def resultEmpty = mkFailure("Expected '" + ch + "'")
    def derive(c: Char) = if (c == ch) success(ch) else new Invalid(resultEmpty)
    def completions(level: Int) = Completions.single(Completion.suggestion(ch.toString))
    override def toString = "'" + ch + "'"
  }

  /** Presents a literal String `s` as a Parser that only parses that exact text and provides it as the result.*/
  implicit def literal(s: String): Parser[String] = stringLiteral(s, 0)

  /** See [[unapply]]. */
  object ~ {

    /** Convenience for destructuring a tuple that mirrors the `~` combinator.*/
    def unapply[A, B](t: (A, B)): Some[(A, B)] = Some(t)

  }

  /** Parses input `str` using `parser`.  If successful, the result is provided wrapped in `Right`.  If unsuccessful, an error message is provided in `Left`.*/
  def parse[T](str: String, parser: Parser[T]): Either[String, T] =
    Parser.result(parser, str).left.map { failures =>
      val (msgs, pos) = failures()
      ProcessError(str, msgs, pos)
    }

  /**
   * Convenience method to use when developing a parser.
   * `parser` is applied to the input `str`.
   * If `completions` is true, the available completions for the input are displayed.
   * Otherwise, the result of parsing is printed using the result's `toString` method.
   * If parsing fails, the error message is displayed.
   *
   * See also [[sampleParse]] and [[sampleCompletions]].
   */
  def sample(str: String, parser: Parser[_], completions: Boolean = false): Unit =
    if (completions) sampleCompletions(str, parser) else sampleParse(str, parser)

  /**
   * Convenience method to use when developing a parser.
   * `parser` is applied to the input `str` and the result of parsing is printed using the result's `toString` method.
   * If parsing fails, the error message is displayed.
   */
  def sampleParse(str: String, parser: Parser[_]): Unit =
    parse(str, parser) match {
      case Left(msg) => println(msg)
      case Right(v)  => println(v)
    }

  /**
   * Convenience method to use when developing a parser.
   * `parser` is applied to the input `str` and the available completions are displayed on separate lines.
   * If parsing fails, the error message is displayed.
   */
  def sampleCompletions(str: String, parser: Parser[_], level: Int = 1): Unit =
    Parser.completions(parser, str, level).get foreach println

  // intended to be temporary pending proper error feedback
  def result[T](p: Parser[T], s: String): Either[() => (Seq[String], Int), T] = {
    def loop(i: Int, a: Parser[T]): Either[() => (Seq[String], Int), T] =
      a match {
        case Invalid(f) => Left(() => (f.errors, i))
        case _ =>
          val ci = i + 1
          if (ci >= s.length)
            a.resultEmpty.toEither.left.map { msgs0 => () =>
              val msgs = msgs0()
              val nonEmpty = if (msgs.isEmpty) "Unexpected end of input" :: Nil else msgs
              (nonEmpty, ci)
            } else
            loop(ci, a derive s(ci))
      }
    loop(-1, p)
  }

  /** Applies parser `p` to input `s`. */
  def apply[T](p: Parser[T])(s: String): Parser[T] =
    (p /: s)(derive1)

  /** Applies parser `p` to a single character of input. */
  def derive1[T](p: Parser[T], c: Char): Parser[T] =
    if (p.valid) p.derive(c) else p

  /**
   * Applies parser `p` to input `s` and returns the completions at verbosity `level`.
   * The interpretation of `level` is up to parser definitions, but 0 is the default by convention,
   * with increasing positive numbers corresponding to increasing verbosity.  Typically no more than
   * a few levels are defined.
   */
  def completions(p: Parser[_], s: String, level: Int): Completions =
    // The x Completions.empty removes any trailing token completions where append.isEmpty
    apply(p)(s).completions(level) x Completions.empty

  def examples[A](a: Parser[A], completions: Set[String], check: Boolean = false): Parser[A] =
    examples(a, new FixedSetExamples(completions), completions.size, check)

  /**
   * @param a the parser to decorate with a source of examples. All validation and parsing is delegated to this parser,
   *          only [[Parser.completions]] is modified.
   * @param completions the source of examples when displaying completions to the user.
   * @param maxNumberOfExamples limits the number of examples that the source of examples should return. This can
   *                            prevent lengthy pauses and avoids bad interactive user experience.
   * @param removeInvalidExamples indicates whether completion examples should be checked for validity (against the given parser). An
   *                              exception is thrown if the example source contains no valid completion suggestions.
   * @tparam A the type of values that are returned by the parser.
   * @return
   */
  def examples[A](
      a: Parser[A],
      completions: ExampleSource,
      maxNumberOfExamples: Int,
      removeInvalidExamples: Boolean
  ): Parser[A] =
    if (a.valid) {
      a.result match {
        case Some(av) => success(av)
        case None =>
          new ParserWithExamples(a, completions, maxNumberOfExamples, removeInvalidExamples)
      }
    } else a

  def matched(
      t: Parser[_],
      seen: Vector[Char] = Vector.empty,
      partial: Boolean = false
  ): Parser[String] =
    t match {
      case i: Invalid => if (partial && seen.nonEmpty) success(seen.mkString) else i
      case _ =>
        if (t.result.isEmpty)
          new MatchedString(t, seen, partial)
        else
          success(seen.mkString)
    }

  /**
   * Establishes delegate parser `t` as a single token of tab completion.
   * When tab completion of part of this token is requested, the completions provided by the delegate `t` or a later derivative are appended to
   * the prefix String already seen by this parser.
   */
  def token[T](t: Parser[T]): Parser[T] = token(t, TokenCompletions.default)

  /**
   * Establishes delegate parser `t` as a single token of tab completion.
   * When tab completion of part of this token is requested, no completions are returned if `hide` returns true for the current tab completion level.
   * Otherwise, the completions provided by the delegate `t` or a later derivative are appended to the prefix String already seen by this parser.
   */
  def token[T](t: Parser[T], hide: Int => Boolean): Parser[T] =
    token(t, TokenCompletions.default.hideWhen(hide))

  /**
   * Establishes delegate parser `t` as a single token of tab completion.
   * When tab completion of part of this token is requested, `description` is displayed for suggestions and no completions are ever performed.
   */
  def token[T](t: Parser[T], description: String): Parser[T] =
    token(t, TokenCompletions.displayOnly(description))

  /**
   * Establishes delegate parser `t` as a single token of tab completion.
   * When tab completion of part of this token is requested, `display` is used as the printed suggestion, but the completions from the delegate
   * parser `t` are used to complete if unambiguous.
   */
  def tokenDisplay[T](t: Parser[T], display: String): Parser[T] =
    token(t, TokenCompletions.overrideDisplay(display))

  def token[T](t: Parser[T], complete: TokenCompletions): Parser[T] =
    mkToken(t, "", complete)

  private[sbt] def mkToken[T](t: Parser[T], seen: String, complete: TokenCompletions): Parser[T] =
    if (t.valid && !t.isTokenStart)
      if (t.result.isEmpty) new TokenStart(t, seen, complete) else t
    else
      t

  def homParser[A](a: Parser[A], b: Parser[A]): Parser[A] = (a, b) match {
    case (Invalid(af), Invalid(bf)) => Invalid(af ++ bf)
    case (Invalid(_), bv)           => bv
    case (av, Invalid(_))           => av
    case (_, _)                     => new HomParser(a, b)
  }

  def not(p: Parser[_], failMessage: String): Parser[Unit] = p.result match {
    case None    => new Not(p, failMessage)
    case Some(_) => failure(failMessage)
  }

  def oneOf[T](p: Seq[Parser[T]]): Parser[T] = p.reduceLeft(_ | _)
  def seq[T](p: Seq[Parser[T]]): Parser[Seq[T]] = seq0(p, Nil)

  def seq0[T](p: Seq[Parser[T]], errors: => Seq[String]): Parser[Seq[T]] = {
    val (newErrors, valid) = separate(p) {
      case Invalid(f) => Left(f.errors _); case ok => Right(ok)
    }
    def combinedErrors = errors ++ newErrors.flatMap(_())
    if (valid.isEmpty) invalid(combinedErrors) else new ParserSeq(valid, combinedErrors)
  }

  def stringLiteral(s: String, start: Int): Parser[String] = {
    val len = s.length
    if (len == 0) sys.error("String literal cannot be empty")
    else if (start >= len) success(s)
    else new StringLiteral(s, start)
  }
}

sealed trait ValidParser[T] extends Parser[T] {
  final def valid = true
  final def failure = None
  final def ifValid[S](p: => Parser[S]): Parser[S] = p
}

private final case class Invalid(fail: Failure) extends Parser[Nothing] {
  def failure = Some(fail)
  def result = None
  def resultEmpty = fail
  def derive(c: Char) = sys.error("Invalid.")
  def completions(level: Int) = Completions.nil
  override def toString = fail.errors.mkString("; ")
  def valid = false
  def ifValid[S](p: => Parser[S]): Parser[S] = this
}

private final case class SoftInvalid(fail: Failure) extends ValidParser[Nothing] {
  def result = None
  def resultEmpty = fail
  def derive(c: Char) = Invalid(fail)
  def completions(level: Int) = Completions.nil
  override def toString = fail.errors.mkString("; ")
}

private final class TrapAndFail[A](a: Parser[A]) extends ValidParser[A] {
  def result = try { a.result } catch { case _: Exception           => None }
  def resultEmpty = try { a.resultEmpty } catch { case e: Exception => fail(e) }

  def derive(c: Char) = try { trapAndFail(a derive c) } catch {
    case e: Exception => Invalid(fail(e))
  }

  def completions(level: Int) = try { a.completions(level) } catch {
    case _: Exception => Completions.nil
  }

  override def toString = "trap(" + a + ")"
  override def isTokenStart = a.isTokenStart
  private[this] def fail(e: Exception): Failure = mkFailure(e.toString)
}

private final class OnFailure[A](a: Parser[A], message: String) extends ValidParser[A] {
  def result = a.result

  def resultEmpty = a.resultEmpty match {
    case _: Failure => mkFailure(message); case v: Value[A] => v
  }

  def derive(c: Char) = onFailure(a derive c, message)
  def completions(level: Int) = a.completions(level)
  override def toString = "(" + a + " !!! \"" + message + "\" )"
  override def isTokenStart = a.isTokenStart
}

private final class SeqParser[A, B](a: Parser[A], b: Parser[B]) extends ValidParser[(A, B)] {
  lazy val result = tuple(a.result, b.result)
  lazy val resultEmpty = a.resultEmpty seq b.resultEmpty

  def derive(c: Char) = {
    val common = a.derive(c) ~ b
    a.resultEmpty match {
      case Value(av)  => common | b.derive(c).map(br => (av, br))
      case _: Failure => common
    }
  }

  def completions(level: Int) = a.completions(level) x b.completions(level)
  override def toString = "(" + a + " ~ " + b + ")"
}

private final class HomParser[A](a: Parser[A], b: Parser[A]) extends ValidParser[A] {
  lazy val result = tuple(a.result, b.result) map (_._1)
  def derive(c: Char) = (a derive c) | (b derive c)
  lazy val resultEmpty = a.resultEmpty or b.resultEmpty
  def completions(level: Int) = a.completions(level) ++ b.completions(level)
  override def toString = "(" + a + " | " + b + ")"
}

private final class HetParser[A, B](a: Parser[A], b: Parser[B]) extends ValidParser[Either[A, B]] {
  lazy val result = tuple(a.result, b.result) map { case (a, _) => Left(a) }
  def derive(c: Char) = (a derive c) || (b derive c)
  lazy val resultEmpty = a.resultEmpty either b.resultEmpty
  def completions(level: Int) = a.completions(level) ++ b.completions(level)
  override def toString = "(" + a + " || " + b + ")"
}

private final class ParserSeq[T](a: Seq[Parser[T]], errors: => Seq[String])
    extends ValidParser[Seq[T]] {
  assert(a.nonEmpty)

  lazy val resultEmpty: Result[Seq[T]] = {
    val res = a.map(_.resultEmpty)
    val (failures, values) = separate(res)(_.toEither)
    //    if(failures.isEmpty) Value(values) else mkFailures(failures.flatMap(_()) ++ errors)
    if (values.nonEmpty) Value(values) else mkFailures(failures.flatMap(_()) ++ errors)
  }

  def result = {
    val success = a.flatMap(_.result)
    if (success.length == a.length) Some(success) else None
  }

  def completions(level: Int) = a.map(_.completions(level)).reduceLeft(_ ++ _)
  def derive(c: Char) = seq0(a.map(_ derive c), errors)

  override def toString = "seq(" + a + ")"
}

private final class BindParser[A, B](a: Parser[A], f: A => Parser[B]) extends ValidParser[B] {
  lazy val result = a.result flatMap (av => f(av).result)
  lazy val resultEmpty = a.resultEmpty flatMap (av => f(av).resultEmpty)

  def completions(level: Int) =
    a.completions(level) flatMap { c =>
      apply(a)(c.append).resultEmpty match {
        case _: Failure => Completions.strict(Set.empty + c)
        case Value(av)  => c x f(av).completions(level)
      }
    }

  def derive(c: Char) = {
    val common = a derive c flatMap f
    a.resultEmpty match {
      case Value(av)  => common | derive1(f(av), c)
      case _: Failure => common
    }
  }

  override def isTokenStart = a.isTokenStart

  override def toString = "bind(" + a + ")"
}

private final class MapParser[A, B](a: Parser[A], f: A => B) extends ValidParser[B] {
  lazy val result = a.result map f
  lazy val resultEmpty = a.resultEmpty map f
  def derive(c: Char) = (a derive c) map f
  def completions(level: Int) = a.completions(level)
  override def isTokenStart = a.isTokenStart
  override def toString = "map(" + a + ")"
}

private final class Filter[T](p: Parser[T], f: T => Boolean, seen: String, msg: String => String)
    extends ValidParser[T] {
  def filterResult(r: Result[T]) = r.filter(f, msg(seen))
  lazy val result = p.result filter f
  lazy val resultEmpty = filterResult(p.resultEmpty)
  def derive(c: Char) = filterParser(p derive c, f, seen + c, msg)

  def completions(level: Int) = p.completions(level) filterS { s =>
    filterResult(apply(p)(s).resultEmpty).isValid
  }

  override def toString = "filter(" + p + ")"
  override def isTokenStart = p.isTokenStart
}

private final class MatchedString(delegate: Parser[_], seenV: Vector[Char], partial: Boolean)
    extends ValidParser[String] {
  lazy val seen = seenV.mkString
  def derive(c: Char) = matched(delegate derive c, seenV :+ c, partial)
  def completions(level: Int) = delegate.completions(level)
  def result = if (delegate.result.isDefined) Some(seen) else None

  def resultEmpty = delegate.resultEmpty match {
    case f: Failure if !partial => f; case _ => Value(seen)
  }

  override def isTokenStart = delegate.isTokenStart
  override def toString = "matched(" + partial + ", " + seen + ", " + delegate + ")"
}

private final class TokenStart[T](delegate: Parser[T], seen: String, complete: TokenCompletions)
    extends ValidParser[T] {
  def derive(c: Char) = mkToken(delegate derive c, seen + c, complete)

  def completions(level: Int) = complete match {
    case dc: TokenCompletions.Delegating =>
      dc.completions(seen, level, delegate.completions(level))
    case fc: TokenCompletions.Fixed => fc.completions(seen, level)
  }

  def result = delegate.result
  def resultEmpty = delegate.resultEmpty
  override def isTokenStart = true
  override def toString = "token('" + complete + ", " + delegate + ")"
}

private final class And[T](a: Parser[T], b: Parser[_]) extends ValidParser[T] {
  lazy val result = tuple(a.result, b.result) map { _._1 }
  def derive(c: Char) = (a derive c) & (b derive c)
  def completions(level: Int) = a.completions(level).filterS(s => apply(b)(s).resultEmpty.isValid)
  lazy val resultEmpty = a.resultEmpty && b.resultEmpty
  override def toString = "(%s) && (%s)".format(a, b)
}

private final class Not(delegate: Parser[_], failMessage: String) extends ValidParser[Unit] {
  def derive(c: Char) = if (delegate.valid) not(delegate derive c, failMessage) else this
  def completions(level: Int) = Completions.empty
  def result = None

  lazy val resultEmpty = delegate.resultEmpty match {
    case _: Failure  => Value(())
    case _: Value[_] => mkFailure(failMessage)
  }

  override def toString = " -(%s)".format(delegate)
}

/**
 * This class wraps an existing parser (the delegate), and replaces the delegate's completions with examples from
 * the given example source.
 *
 * This class asks the example source for a limited amount of examples (to prevent lengthy and expensive
 * computations and large amounts of allocated data). It then passes these examples on to the UI.
 *
 * @param delegate the parser to decorate with completion examples (i.e., completion of user input).
 * @param exampleSource the source from which this class will take examples (potentially filter them with the delegate
 *                      parser), and pass them to the UI.
 * @param maxNumberOfExamples the maximum number of completions to read from the example source and pass to the UI. This
 *                            limit prevents lengthy example generation and allocation of large amounts of memory.
 * @param removeInvalidExamples indicates whether to remove examples that are deemed invalid by the delegate parser.
 * @tparam T the type of value produced by the parser.
 */
private final class ParserWithExamples[T](
    delegate: Parser[T],
    exampleSource: ExampleSource,
    maxNumberOfExamples: Int,
    removeInvalidExamples: Boolean
) extends ValidParser[T] {

  def derive(c: Char) =
    examples(delegate derive c,
             exampleSource.withAddedPrefix(c.toString),
             maxNumberOfExamples,
             removeInvalidExamples)

  def result = delegate.result

  lazy val resultEmpty = delegate.resultEmpty

  def completions(level: Int) = {
    if (exampleSource().isEmpty)
      if (resultEmpty.isValid) Completions.nil else Completions.empty
    else {
      val examplesBasedOnTheResult = filteredExamples.take(maxNumberOfExamples).toSet
      Completions(examplesBasedOnTheResult.map(ex => Completion.suggestion(ex)))
    }
  }

  override def toString = "examples(" + delegate + ", " + exampleSource().take(2).toList + ")"

  private def filteredExamples: Iterable[String] = {
    if (removeInvalidExamples)
      exampleSource().filter(isExampleValid)
    else
      exampleSource()
  }

  private def isExampleValid(example: String): Boolean = {
    apply(delegate)(example).resultEmpty.isValid
  }
}

private final class StringLiteral(str: String, start: Int) extends ValidParser[String] {
  assert(0 <= start && start < str.length)

  def failMsg = "Expected '" + str + "'"
  def resultEmpty = mkFailure(failMsg)
  def result = None

  def derive(c: Char) =
    if (str.charAt(start) == c) stringLiteral(str, start + 1) else new Invalid(resultEmpty)

  def completions(level: Int) = Completions.single(Completion.suggestion(str.substring(start)))
  override def toString = '"' + str + '"'
}

private final class CharacterClass(f: Char => Boolean, label: String) extends ValidParser[Char] {
  def result = None
  def resultEmpty = mkFailure("Expected " + label)
  def derive(c: Char) = if (f(c)) success(c) else Invalid(resultEmpty)
  def completions(level: Int) = Completions.empty
  override def toString = "class(" + label + ")"
}

private final class Optional[T](delegate: Parser[T]) extends ValidParser[Option[T]] {
  def result = delegate.result map some.fn
  def resultEmpty = Value(None)
  def derive(c: Char) = (delegate derive c).map(some.fn)
  def completions(level: Int) = Completion.empty +: delegate.completions(level)
  override def toString = delegate.toString + "?"
}

private final class Repeat[T](
    partial: Option[Parser[T]],
    repeated: Parser[T],
    min: Int,
    max: UpperBound,
    accumulatedReverse: List[T]
) extends ValidParser[Seq[T]] {
  assume(0 <= min, "Minimum occurences must be non-negative")
  assume(max >= min, "Minimum occurences must be less than the maximum occurences")

  def derive(c: Char) =
    partial match {
      case Some(part) =>
        val partD = repeat(Some(part derive c), repeated, min, max, accumulatedReverse)
        part.resultEmpty match {
          case Value(pv)  => partD | repeatDerive(c, pv :: accumulatedReverse)
          case _: Failure => partD
        }
      case None => repeatDerive(c, accumulatedReverse)
    }

  def repeatDerive(c: Char, accRev: List[T]): Parser[Seq[T]] =
    repeat(Some(repeated derive c), repeated, scala.math.max(0, min - 1), max.decrement, accRev)

  def completions(level: Int) = {
    def pow(comp: Completions, exp: Completions, n: Int): Completions =
      if (n == 1) comp else pow(comp x exp, exp, n - 1)

    val repC = repeated.completions(level)
    val fin = if (min == 0) Completion.empty +: repC else pow(repC, repC, min)
    partial match {
      case Some(p) => p.completions(level) x fin
      case None    => fin
    }
  }

  def result = None

  lazy val resultEmpty: Result[Seq[T]] = {
    val partialAccumulatedOption =
      partial match {
        case None                 => Value(accumulatedReverse)
        case Some(partialPattern) => partialPattern.resultEmpty.map(_ :: accumulatedReverse)
      }
    (partialAccumulatedOption app repeatedParseEmpty)(_ reverse_::: _)
  }

  private def repeatedParseEmpty: Result[List[T]] = {
    if (min == 0)
      Value(Nil)
    else
      // forced determinism
      for (value <- repeated.resultEmpty) yield makeList(min, value)
  }

  override def toString = "repeat(" + min + "," + max + "," + partial + "," + repeated + ")"
}
