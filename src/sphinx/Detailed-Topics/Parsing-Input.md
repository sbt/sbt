# Parsing and tab completion

This page describes the parser combinators in sbt.
These parser combinators are typically used to parse user input and provide tab completion for [[Input Tasks]] and [[Commands]].
If you are already familiar with Scala's parser combinators, the methods are mostly the same except that their arguments are strict.
There are two additional methods for controlling tab completion that are discussed at the end of the section.

Parser combinators build up a parser from smaller parsers.
A `Parser[T]` in its most basic usage is a function `String => Option[T]`.
It accepts a `String` to parse and produces a value wrapped in `Some` if parsing succeeds or `None` if it fails.
Error handling and tab completion make this picture more complicated, but we'll stick with Option for this discussion.

The following examples assume the imports:
```scala
import sbt._
import complete.DefaultParsers._
```

## Basic parsers

The simplest parser combinators match exact inputs:

```scala
// A parser that succeeds if the input is 'x', returning the Char 'x'
//  and failing otherwise
val singleChar: Parser[Char] = 'x'

// A parser that succeeds if the input is "blue", returning the String "blue"
//   and failing otherwise
val litString: Parser[String] = "blue"
```

In these examples, implicit conversions produce a literal `Parser` from a `Char` or `String`.
Other basic parser constructors are the `charClass`, `success` and `failure` methods:

```scala
// A parser that succeeds if the character is a digit, returning the matched Char 
//   The second argument, "digit", describes the parser and is used in error messages
val digit: Parser[Char] = charClass( (c: Char) => c.isDigit, "digit")

// A parser that produces the value 3 for an empty input string, fails otherwise
val alwaysSucceed: Parser[Int] = success( 3 )

// Represents failure (always returns None for an input String).
//  The argument is the error message.
val alwaysFail: Parser[Nothing] = failure("Invalid input.")
```

## Combining parsers

We build on these basic parsers to construct more interesting parsers.
We can combine parsers in a sequence, choose between parsers, or repeat a parser.

```scala
// A parser that succeeds if the input is "blue" or "green",
//  returning the matched input
val color: Parser[String] = "blue" | "green"

// A parser that matches either "fg" or "bg"
val select: Parser[String] = "fg" | "bg"

// A parser that matches "fg" or "bg", a space, and then the color, returning the matched values.
//   ~ is an alias for Tuple2.
val setColor: Parser[String ~ Char ~ String] =
  select ~ ' ' ~ color
 
// Often, we don't care about the value matched by a parser, such as the space above
//  For this, we can use ~> or <~, which keep the result of
//  the parser on the right or left, respectively
val setColor2: Parser[String ~ String]  =  select ~ (' ' ~> color)

// Match one or more digits, returning a list of the matched characters
val digits: Parser[Seq[Char]]  =  charClass(_.isDigit, "digit").+

// Match zero or more digits, returning a list of the matched characters
val digits0: Parser[Seq[Char]]  =  charClass(_.isDigit, "digit").*

// Optionally match a digit
val optDigit: Parser[Option[Char]]  =  charClass(_.isDigit, "digit").?
```

## Transforming results

A key aspect of parser combinators is transforming results along the way into more useful data structures.
The fundamental methods for this are `map` and `flatMap`.
Here are examples of `map` and some convenience methods implemented on top of `map`.

```scala
// Apply the `digits` parser and apply the provided function to the matched
//   character sequence
val num: Parser[Int] = digits map { (chars: Seq[Char]) => chars.mkString.toInt }

// Match a digit character, returning the matched character or return '0' if the input is not a digit
val digitWithDefault: Parser[Char]  =  charClass(_.isDigit, "digit") ?? '0'

// The previous example is equivalent to:
val digitDefault: Parser[Char] =
  charClass(_.isDigit, "digit").? map { (d: Option[Char]) => d getOrElse '0' }
  
// Succeed if the input is "blue" and return the value 4
val blue = "blue" ^^^ 4

// The above is equivalent to:
val blueM = "blue" map { (s: String) => 4 }
```

## Controlling tab completion

Most parsers have reasonable default tab completion behavior.
For example, the string and character literal parsers will suggest the underlying literal for an empty input string.
However, it is impractical to determine the valid completions for `charClass`, since it accepts an arbitrary predicate.
The `examples` method defines explicit completions for such a parser:

```scala
val digit = charClass(_.isDigit, "digit").examples("0", "1", "2")
```

Tab completion will use the examples as suggestions.
The other method controlling tab completion is `token`.
The main purpose of `token` is to determine the boundaries for suggestions.
For example, if your parser is:

```scala
("fg" | "bg") ~ ' ' ~ ("green" | "blue")
```

then the potential completions on empty input are:
```console
fg green
fg blue
bg green
bg blue
```

Typically, you want to suggest smaller segments or the number of suggestions becomes unmanageable.
A better parser is:

```scala
token( ("fg" | "bg") ~ ' ') ~ token("green" | "blue")
```

Now, the initial suggestions would be (with _ representing a space):
```console
fg_
bg_
```

Be careful not to overlap or nest tokens, as in `token("green" ~ token("blue"))`.  The behavior is unspecified (and should generate an error in the future), but typically the outer most token definition will be used.
