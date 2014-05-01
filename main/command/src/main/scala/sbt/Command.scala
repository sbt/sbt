/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt

import java.io.File
import complete.{ DefaultParsers, EditDistance, Parser }
import Types.const

sealed trait Command {
  def help: State => Help
  def parser: State => Parser[() => State]
  def tags: AttributeMap
  def tag[T](key: AttributeKey[T], value: T): Command
}
private[sbt] final class SimpleCommand(val name: String, private[sbt] val help0: Help, val parser: State => Parser[() => State], val tags: AttributeMap) extends Command {
  assert(Command validID name, "'" + name + "' is not a valid command name.")
  def tag[T](key: AttributeKey[T], value: T): SimpleCommand = new SimpleCommand(name, help0, parser, tags.put(key, value))
  def help = const(help0)
}
private[sbt] final class ArbitraryCommand(val parser: State => Parser[() => State], val help: State => Help, val tags: AttributeMap) extends Command {
  def tag[T](key: AttributeKey[T], value: T): ArbitraryCommand = new ArbitraryCommand(parser, help, tags.put(key, value))
}

object Command {
  import DefaultParsers._

  def command(name: String, briefHelp: String, detail: String)(f: State => State): Command = command(name, Help(name, (name, briefHelp), detail))(f)
  def command(name: String, help: Help = Help.empty)(f: State => State): Command = make(name, help)(state => success(() => f(state)))

  def make(name: String, briefHelp: (String, String), detail: String)(parser: State => Parser[() => State]): Command =
    make(name, Help(name, briefHelp, detail))(parser)
  def make(name: String, help: Help = Help.empty)(parser: State => Parser[() => State]): Command = new SimpleCommand(name, help, parser, AttributeMap.empty)

  def apply[T](name: String, briefHelp: (String, String), detail: String)(parser: State => Parser[T])(effect: (State, T) => State): Command =
    apply(name, Help(name, briefHelp, detail))(parser)(effect)
  def apply[T](name: String, help: Help = Help.empty)(parser: State => Parser[T])(effect: (State, T) => State): Command =
    make(name, help)(applyEffect(parser)(effect))

  def args(name: String, briefHelp: (String, String), detail: String, display: String)(f: (State, Seq[String]) => State): Command =
    args(name, display, Help(name, briefHelp, detail))(f)

  def args(name: String, display: String, help: Help = Help.empty)(f: (State, Seq[String]) => State): Command =
    make(name, help)(state => spaceDelimited(display) map apply1(f, state))

  def single(name: String, briefHelp: (String, String), detail: String)(f: (State, String) => State): Command =
    single(name, Help(name, briefHelp, detail))(f)
  def single(name: String, help: Help = Help.empty)(f: (State, String) => State): Command =
    make(name, help)(state => token(trimmed(spacedAny(name)) map apply1(f, state)))

  def custom(parser: State => Parser[() => State], help: Help = Help.empty): Command = customHelp(parser, const(help))
  def customHelp(parser: State => Parser[() => State], help: State => Help): Command = new ArbitraryCommand(parser, help, AttributeMap.empty)
  def arb[T](parser: State => Parser[T], help: Help = Help.empty)(effect: (State, T) => State): Command = custom(applyEffect(parser)(effect), help)

  def validID(name: String) = DefaultParsers.matches(OpOrID, name)

  def applyEffect[T](parser: State => Parser[T])(effect: (State, T) => State): State => Parser[() => State] =
    s => applyEffect(parser(s))(t => effect(s, t))
  def applyEffect[T](p: Parser[T])(f: T => State): Parser[() => State] =
    p map { t => () => f(t) }

  def combine(cmds: Seq[Command]): State => Parser[() => State] =
    {
      val (simple, arbs) = separateCommands(cmds)
      state => (simpleParser(simple)(state) /: arbs.map(_ parser state)) { _ | _ }
    }
  private[this] def separateCommands(cmds: Seq[Command]): (Seq[SimpleCommand], Seq[ArbitraryCommand]) =
    Util.separate(cmds) { case s: SimpleCommand => Left(s); case a: ArbitraryCommand => Right(a) }
  private[this] def apply1[A, B, C](f: (A, B) => C, a: A): B => () => C =
    b => () => f(a, b)

  def simpleParser(cmds: Seq[SimpleCommand]): State => Parser[() => State] =
    simpleParser(cmds.map(sc => (sc.name, argParser(sc))).toMap)
  private[this] def argParser(sc: SimpleCommand): State => Parser[() => State] =
    {
      def usageError = s"${sc.name} usage:" + Help.message(sc.help0, None)
      s => (Parser.softFailure(usageError, definitive = true): Parser[() => State]) | sc.parser(s)
    }

  def simpleParser(commandMap: Map[String, State => Parser[() => State]]): State => Parser[() => State] =
    (state: State) => token(OpOrID examples commandMap.keys.toSet) flatMap { id =>
      (commandMap get id) match {
        case None    => failure(invalidValue("command", commandMap.keys)(id))
        case Some(c) => c(state)
      }
    }

  def process(command: String, state: State): State =
    {
      val parser = combine(state.definedCommands)
      parse(command, parser(state)) match {
        case Right(s) => s() // apply command.  command side effects happen here
        case Left(errMsg) =>
          state.log.error(errMsg)
          state.fail
      }
    }
  def invalidValue(label: String, allowed: Iterable[String])(value: String): String =
    "Not a valid " + label + ": " + value + similar(value, allowed)
  def similar(value: String, allowed: Iterable[String]): String =
    {
      val suggested = if (value.length > 2) suggestions(value, allowed.toSeq) else Nil
      if (suggested.isEmpty) "" else suggested.mkString(" (similar: ", ", ", ")")
    }
  def suggestions(a: String, bs: Seq[String], maxDistance: Int = 3, maxSuggestions: Int = 3): Seq[String] =
    bs.map { b => (b, distance(a, b)) } filter (_._2 <= maxDistance) sortBy (_._2) take (maxSuggestions) map (_._1)
  def distance(a: String, b: String): Int =
    EditDistance.levenshtein(a, b, insertCost = 1, deleteCost = 1, subCost = 2, transposeCost = 1, matchCost = -1, caseCost = 1, true)

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
private final class Help0(val brief: Seq[(String, String)], val detail: Map[String, String], val more: Set[String]) extends Help {
  def ++(h: Help): Help = new Help0(Help0.this.brief ++ h.brief, Help0.this.detail ++ h.detail, more ++ h.more)
}
object Help {
  val empty: Help = briefDetail(Nil)

  def apply(name: String, briefHelp: (String, String), detail: String): Help = apply(briefHelp, Map((name, detail)))

  def apply(briefHelp: (String, String), detailedHelp: Map[String, String] = Map.empty): Help =
    apply(briefHelp :: Nil, detailedHelp)

  def apply(briefHelp: Seq[(String, String)], detailedHelp: Map[String, String]): Help =
    apply(briefHelp, detailedHelp, Set.empty[String])
  def apply(briefHelp: Seq[(String, String)], detailedHelp: Map[String, String], more: Set[String]): Help =
    new Help0(briefHelp, detailedHelp, more)

  def more(name: String, detailedHelp: String): Help = apply(Nil, Map(name -> detailedHelp), Set(name))
  def briefDetail(help: Seq[(String, String)]): Help = apply(help, help.toMap)
  def briefOnly(help: Seq[(String, String)]): Help = apply(help, Map.empty[String, String])
  def detailOnly(help: Seq[(String, String)]): Help = apply(Nil, help.toMap)

  import CommandUtil._

  def message(h: Help, arg: Option[String]): String =
    arg match {
      case Some(x) => detail(x, h.detail)
      case None =>
        val brief = aligned("  ", "   ", h.brief).mkString("\n", "\n", "\n")
        val more = h.more.toSeq.sorted
        if (more.isEmpty)
          brief
        else
          brief + "\n" + moreMessage(more)
    }
  def moreMessage(more: Seq[String]): String =
    more.mkString("More command help available using 'help <command>' for:\n  ", ", ", "\n")
}
trait CommandDefinitions extends (State => State) {
  def commands: Seq[Command] = ReflectUtilities.allVals[Command](this).values.toSeq
  def apply(s: State): State = s ++ commands
}
