/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt

	import java.io.File
	import complete.{DefaultParsers, EditDistance, Parser}
	import CommandSupport.logger

sealed trait Command {
	def help: Seq[Help]
	def parser: State => Parser[() => State]
	def tags: AttributeMap
	def tag[T](key: AttributeKey[T], value: T): Command
}
private[sbt] final class SimpleCommand(val name: String, val help: Seq[Help], val parser: State => Parser[() => State], val tags: AttributeMap) extends Command {
	assert(Command validID name, "'" + name + "' is not a valid command name." )
	def tag[T](key: AttributeKey[T], value: T): SimpleCommand = new SimpleCommand(name, help, parser, tags.put(key, value))
}
private[sbt] final class ArbitraryCommand(val parser: State => Parser[() => State], val help: Seq[Help], val tags: AttributeMap) extends Command
{
	def tag[T](key: AttributeKey[T], value: T): ArbitraryCommand = new ArbitraryCommand(parser, help, tags.put(key, value))
}

object Command
{
	def pointerSpace(s: String, i: Int): String  =	(s take i) map { case '\t' => '\t'; case _ => ' ' } mkString;
	
		import DefaultParsers._

	def command(name: String)(f: State => State): Command  =  command(name, Nil)(f)
	def command(name: String, briefHelp: String, detail: String)(f: State => State): Command  =  command(name, Help(name, (name, briefHelp), detail) :: Nil)(f)
	def command(name: String, help: Seq[Help])(f: State => State): Command  =  make(name, help : _*)(state => success(() => f(state)))

	def make(name: String, briefHelp: (String, String), detail: String)(parser: State => Parser[() => State]): Command =
		make(name, Help(name, briefHelp, detail) )(parser)
	def make(name: String, help: Help*)(parser: State => Parser[() => State]): Command  =  new SimpleCommand(name, help, parser, AttributeMap.empty)

	def apply[T](name: String, briefHelp: (String, String), detail: String)(parser: State => Parser[T])(effect: (State,T) => State): Command =
		apply(name, Help(name, briefHelp, detail) )(parser)(effect)
	def apply[T](name: String, help: Help*)(parser: State => Parser[T])(effect: (State,T) => State): Command  =
		make(name, help : _* )(applyEffect(parser)(effect) )

	def args(name: String, briefHelp: (String, String), detail: String, display: String)(f: (State, Seq[String]) => State): Command =
		args(name, display, Help(name, briefHelp, detail) )(f)
	
	def args(name: String, display: String, help: Help*)(f: (State, Seq[String]) => State): Command =
		make(name, help : _*)( state => spaceDelimited(display) map apply1(f, state) )

	def single(name: String, briefHelp: (String, String), detail: String)(f: (State, String) => State): Command =
		single(name, Help(name, briefHelp, detail) )(f)
	def single(name: String, help: Help*)(f: (State, String) => State): Command =
		make(name, help : _*)( state => token(trimmed(spacedAny(name)) map apply1(f, state)) )

	def custom(parser: State => Parser[() => State], help: Seq[Help] = Nil): Command  =  new ArbitraryCommand(parser, help, AttributeMap.empty)
	def arb[T](parser: State => Parser[T], help: Help*)(effect: (State, T) => State): Command  =  custom(applyEffect(parser)(effect), help)

	def validID(name: String) = DefaultParsers.matches(OpOrID, name)

	def applyEffect[T](parser: State => Parser[T])(effect: (State, T) => State): State => Parser[() => State] =
		s => applyEffect(parser(s))(t => effect(s,t))
	def applyEffect[T](p: Parser[T])(f: T => State): Parser[() => State] =
		p map { t => () => f(t) }

	def combine(cmds: Seq[Command]): State => Parser[() => State] =
	{
		val (simple, arbs) = separateCommands(cmds)
		state => (simpleParser(simple)(state) /: arbs.map(_ parser state) ){ _ | _ }
	}
	private[this] def separateCommands(cmds: Seq[Command]): (Seq[SimpleCommand], Seq[ArbitraryCommand]) =
		Util.separate(cmds){ case s: SimpleCommand => Left(s); case a: ArbitraryCommand => Right(a) }
	private[this] def apply1[A,B,C](f: (A,B) => C, a: A): B => () => C =
		b => () => f(a,b)

	def simpleParser(cmds: Seq[SimpleCommand]): State => Parser[() => State] =
		simpleParser(cmds.map(sc => (sc.name, sc.parser)).toMap )

	def simpleParser(commandMap: Map[String, State => Parser[() => State]]): State => Parser[() => State] =
		(state: State) => token(OpOrID examples commandMap.keys.toSet) flatMap { id =>
			(commandMap get id) match {
				case None => failure(invalidValue("command", commandMap.keys)(id))
				case Some(c) => c(state)
			}
		}
		
	def process(command: String, state: State): State =
	{
		val parser = combine(state.definedCommands)
		Parser.result(parser(state), command) match
		{
			case Right(s) => s() // apply command.  command side effects happen here
			case Left(failures) =>
				val (msgs,pos) = failures()
				val errMsg = commandError(command, msgs, pos)
				logger(state).error(errMsg)
				state.fail				
		}
	}
	def commandError(command: String, msgs: Seq[String], index: Int): String =
	{
		val (line, modIndex) = extractLine(command, index)
		val point = pointerSpace(command, modIndex)
		msgs.mkString("\n") + "\n" + line + "\n" + point + "^"
	}
	def extractLine(s: String, i: Int): (String, Int) =
	{
		val notNewline = (c: Char) => c != '\n' && c != '\r'
		val left = takeRightWhile( s.substring(0, i) )( notNewline )
		val right = s substring i takeWhile notNewline
		(left + right, left.length)
	}
	def takeRightWhile(s: String)(pred: Char => Boolean): String =
	{
		def loop(i: Int): String =
			if(i < 0)
				s
			else if( pred(s(i)) )
				loop(i-1)
			else
				s.substring(i+1)
		loop(s.length - 1)
	}
	def invalidValue(label: String, allowed: Iterable[String])(value: String): String =
		"Not a valid " + label + ": " + value + similar(value, allowed)
	def similar(value: String, allowed: Iterable[String]): String =
	{
		val suggested = suggestions(value, allowed.toSeq)
		if(suggested.isEmpty) "" else suggested.mkString(" (similar: ", ", ", ")")
	}
	def suggestions(a: String, bs: Seq[String], maxDistance: Int = 3, maxSuggestions: Int = 3): Seq[String] =
		bs.map { b => (b, distance(a, b) ) } filter (_._2 <= maxDistance) sortBy(_._2) take(maxSuggestions) map(_._1)
	def distance(a: String, b: String): Int =
		EditDistance.levenshtein(a, b, insertCost = 1, deleteCost = 1, subCost = 2, transposeCost = 1, matchCost = -1, true)

	def spacedAny(name: String): Parser[String] = spacedC(name, any)
	def spacedC(name: String, c: Parser[Char]): Parser[String] =
		( (c & opOrIDSpaced(name)) ~ c.+) map { case (f, rem) => (f +: rem).mkString }
}

trait Help
{
	def detail: (Set[String], String)
	def brief: (String, String)
}
object Help
{
	def apply(name: String, briefHelp: (String, String), detail: String): Help  =  apply(briefHelp, (Set(name), detail))

	def apply(briefHelp: (String, String), detailedHelp: (Set[String], String) = (Set.empty, "") ): Help =
		new Help { def detail = detailedHelp; def brief = briefHelp }
}
trait CommandDefinitions
{
	def commands: Seq[Command]
}
trait ReflectedCommands extends CommandDefinitions
{
	def commands = ReflectUtilities.allVals[Command](this).values.toSeq
}