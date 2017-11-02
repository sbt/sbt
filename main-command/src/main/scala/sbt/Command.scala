/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import sbt.internal.inc.ReflectUtilities
import sbt.internal.util.complete.{ DefaultParsers, EditDistance, Parser }
import sbt.internal.util.Types.const
import sbt.internal.util.{ AttributeKey, AttributeMap, Util }

/**
 * An operation that can be executed from the sbt console.
 *
 * <p>The operation takes a [[sbt.State State]] as a parameter and returns a [[sbt.State State]].
 * This means that a command can look at or modify other sbt settings, for example.
 * Typically you would resort to a command when you need to do something that's impossible in a regular task.
 */
sealed trait Command {
  def help: State => Help
  def parser: State => Parser[() => State]

  def tags: AttributeMap
  def tag[T](key: AttributeKey[T], value: T): Command

  def nameOption: Option[String] = this match {
    case sc: SimpleCommand => Some(sc.name)
    case _                 => None
  }
}

private[sbt] final class SimpleCommand(
    val name: String,
    private[sbt] val help0: Help,
    val parser: State => Parser[() => State],
    val tags: AttributeMap
) extends Command {

  assert(Command validID name, s"'$name' is not a valid command name.")

  def help = const(help0)

  def tag[T](key: AttributeKey[T], value: T): SimpleCommand =
    new SimpleCommand(name, help0, parser, tags.put(key, value))

  override def toString = s"SimpleCommand($name)"
}

private[sbt] final class ArbitraryCommand(
    val parser: State => Parser[() => State],
    val help: State => Help,
    val tags: AttributeMap
) extends Command {
  def tag[T](key: AttributeKey[T], value: T): ArbitraryCommand =
    new ArbitraryCommand(parser, help, tags.put(key, value))
}

object Command {
  import DefaultParsers._

  // Lowest-level command construction

  def make(name: String, help: Help = Help.empty)(parser: State => Parser[() => State]): Command =
    new SimpleCommand(name, help, parser, AttributeMap.empty)

  def make(name: String, briefHelp: (String, String), detail: String)(
      parser: State => Parser[() => State]): Command =
    make(name, Help(name, briefHelp, detail))(parser)

  // General command construction

  /** Construct a command with the given name, parser and effect. */
  def apply[T](name: String, help: Help = Help.empty)(parser: State => Parser[T])(
      effect: (State, T) => State): Command =
    make(name, help)(applyEffect(parser)(effect))

  def apply[T](name: String, briefHelp: (String, String), detail: String)(
      parser: State => Parser[T])(effect: (State, T) => State): Command =
    apply(name, Help(name, briefHelp, detail))(parser)(effect)

  // No-argument command construction

  /** Construct a no-argument command with the given name and effect. */
  def command(name: String, help: Help = Help.empty)(f: State => State): Command =
    make(name, help)(state => success(() => f(state)))

  def command(name: String, briefHelp: String, detail: String)(f: State => State): Command =
    command(name, Help(name, (name, briefHelp), detail))(f)

  // Single-argument command construction

  /** Construct a single-argument command with the given name and effect. */
  def single(name: String, help: Help = Help.empty)(f: (State, String) => State): Command =
    make(name, help)(state => token(trimmed(spacedAny(name)) map apply1(f, state)))

  def single(name: String, briefHelp: (String, String), detail: String)(
      f: (State, String) => State): Command =
    single(name, Help(name, briefHelp, detail))(f)

  // Multi-argument command construction

  /** Construct a multi-argument command with the given name, tab completion display and effect. */
  def args(name: String, display: String, help: Help = Help.empty)(
      f: (State, Seq[String]) => State): Command =
    make(name, help)(state => spaceDelimited(display) map apply1(f, state))

  def args(name: String, briefHelp: (String, String), detail: String, display: String)(
      f: (State, Seq[String]) => State): Command =
    args(name, display, Help(name, briefHelp, detail))(f)

  // create ArbitraryCommand

  def customHelp(parser: State => Parser[() => State], help: State => Help): Command =
    new ArbitraryCommand(parser, help, AttributeMap.empty)

  def custom(parser: State => Parser[() => State], help: Help = Help.empty): Command =
    customHelp(parser, const(help))

  def arb[T](parser: State => Parser[T], help: Help = Help.empty)(
      effect: (State, T) => State): Command =
    custom(applyEffect(parser)(effect), help)

  // misc Command object utilities

  def validID(name: String): Boolean = DefaultParsers.matches(OpOrID, name)

  def applyEffect[T](p: Parser[T])(f: T => State): Parser[() => State] = p map (t => () => f(t))

  def applyEffect[T](parser: State => Parser[T])(
      effect: (State, T) => State): State => Parser[() => State] =
    s => applyEffect(parser(s))(t => effect(s, t))

  def combine(cmds: Seq[Command]): State => Parser[() => State] = {
    val (simple, arbs) = separateCommands(cmds)
    state =>
      (simpleParser(simple)(state) /: arbs.map(_ parser state))(_ | _)
  }

  private[this] def separateCommands(
      cmds: Seq[Command]): (Seq[SimpleCommand], Seq[ArbitraryCommand]) =
    Util.separate(cmds) { case s: SimpleCommand => Left(s); case a: ArbitraryCommand => Right(a) }

  private[this] def apply1[A, B, C](f: (A, B) => C, a: A): B => () => C = b => () => f(a, b)

  def simpleParser(cmds: Seq[SimpleCommand]): State => Parser[() => State] =
    simpleParser(cmds.map(sc => (sc.name, argParser(sc))).toMap)

  private[this] def argParser(sc: SimpleCommand): State => Parser[() => State] = {
    def usageError = s"${sc.name} usage:" + Help.message(sc.help0, None)
    s =>
      (Parser.softFailure(usageError, definitive = true): Parser[() => State]) | sc.parser(s)
  }

  def simpleParser(
      commandMap: Map[String, State => Parser[() => State]]): State => Parser[() => State] =
    state =>
      token(OpOrID examples commandMap.keys.toSet) flatMap (id =>
        (commandMap get id) match {
          case None    => failure(invalidValue("command", commandMap.keys)(id))
          case Some(c) => c(state)
        })

  def invalidValue(label: String, allowed: Iterable[String])(value: String): String =
    s"Not a valid $label: $value" + similar(value, allowed)

  def similar(value: String, allowed: Iterable[String]): String = {
    val suggested = if (value.length > 2) suggestions(value, allowed.toSeq) else Nil
    if (suggested.isEmpty) "" else suggested.mkString(" (similar: ", ", ", ")")
  }

  def suggestions(a: String,
                  bs: Seq[String],
                  maxDistance: Int = 3,
                  maxSuggestions: Int = 3): Seq[String] =
    bs map (b => (b, distance(a, b))) filter (_._2 <= maxDistance) sortBy (_._2) take (maxSuggestions) map (_._1)

  def distance(a: String, b: String): Int =
    EditDistance.levenshtein(a,
                             b,
                             insertCost = 1,
                             deleteCost = 1,
                             subCost = 2,
                             transposeCost = 1,
                             matchCost = -1,
                             caseCost = 1,
                             transpositions = true)

  def spacedAny(name: String): Parser[String] = spacedC(name, any)

  def spacedC(name: String, c: Parser[Char]): Parser[String] =
    ((c & opOrIDSpaced(name)) ~ c.+) map { case (f, rem) => (f +: rem).mkString }
}

trait Help {
  def detail: Map[String, String]
  def brief: Seq[(String, String)]
  def more: Set[String]
  def ++(o: Help): Help
}

private final class Help0(
    val brief: Seq[(String, String)],
    val detail: Map[String, String],
    val more: Set[String]
) extends Help {
  def ++(h: Help): Help =
    new Help0(Help0.this.brief ++ h.brief, Help0.this.detail ++ h.detail, more ++ h.more)
}

object Help {
  val empty: Help = briefDetail(Nil)

  def apply(name: String, briefHelp: (String, String), detail: String): Help =
    apply(briefHelp, Map((name, detail)))

  def apply(briefHelp: (String, String), detailedHelp: Map[String, String] = Map.empty): Help =
    apply(briefHelp :: Nil, detailedHelp)

  def apply(briefHelp: Seq[(String, String)], detailedHelp: Map[String, String]): Help =
    apply(briefHelp, detailedHelp, Set.empty[String])

  def apply(briefHelp: Seq[(String, String)],
            detailedHelp: Map[String, String],
            more: Set[String]): Help =
    new Help0(briefHelp, detailedHelp, more)

  def more(name: String, detailedHelp: String): Help =
    apply(Nil, Map(name -> detailedHelp), Set(name))
  def briefDetail(help: Seq[(String, String)]): Help = apply(help, help.toMap)
  def briefOnly(help: Seq[(String, String)]): Help = apply(help, Map.empty[String, String])
  def detailOnly(help: Seq[(String, String)]): Help = apply(Nil, help.toMap)

  import CommandUtil._

  def message(h: Help, arg: Option[String]): String =
    arg match {
      case Some(x) => detail(x, h.detail)
      case None =>
        val brief = aligned("  ", "   ", h.brief).mkString("\n", "\n", "\n")
        val more = h.more
        if (more.isEmpty)
          brief
        else
          brief + "\n" + moreMessage(more.toSeq.sorted)
    }

  def moreMessage(more: Seq[String]): String =
    more.mkString("More command help available using 'help <command>' for:\n  ", ", ", "\n")
}

trait CommandDefinitions extends (State => State) {
  def commands: Seq[Command] = ReflectUtilities.allVals[Command](this).values.toSeq
  def apply(s: State): State = s ++ commands
}
