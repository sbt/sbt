package xsbt.boot

import scala.util.parsing.combinator.Parsers
import scala.util.parsing.input.{Reader, StreamReader}

import java.lang.Character.isWhitespace
import java.io.File
import java.net.{MalformedURLException, URL}
import scala.collection.immutable.TreeMap

class ConfigurationParser extends Parsers with NotNull
{
	import scala.util.parsing.input.CharSequenceReader
	def apply(s: String): LaunchConfiguration = processResult(configuration(new CharSequenceReader(s, 0)))
	def apply(source: java.io.Reader): LaunchConfiguration = processResult(configuration(StreamReader(source)))

	def processResult(r: ParseResult[LaunchConfiguration]) =
		r match
		{
			case Success(r, _) => r
			case _ => throw new BootException(r.toString)
		}

	// section -> configuration instance  processing
	lazy val configuration = phrase(sections ^^ processSections)
	def processSections(sections: SectionMap): LaunchConfiguration =
	{
		val (scalaVersion, m1) = processSection(sections, "scala", getScalaVersion)
		val (app, m2) = processSection(m1, "app", getApplication)
		val (repositories, m3) = processSection(m2, "repositories", getRepositories)
		val (boot, m4) = processSection(m3, "boot", getBoot)
		val (logging, m5) = processSection(m4, "log", getLogging)
		val (properties, m6) = processSection(m5, "app-properties", getAppProperties)
		check(m6, "section")
		new LaunchConfiguration(scalaVersion, app, repositories, boot, logging, properties)
	}
	def getScalaVersion(m: LabelMap) = check("label", getVersion(m))
	def getVersion(m: LabelMap): (Version, LabelMap) = process(m, "version", processVersion)
	def processVersion(value: Option[String]): Version = value.map(version).getOrElse(Version.default)
	def version(value: String): Version =
	{
		if(value.isEmpty) throw new BootException("Version cannot be empty (omit version declaration to use the default version)")
		val tokens = trim(value.split(",", 2))
		import Version.{Explicit, Implicit}
		val defaultVersion = if(tokens.length == 2) Some(tokens(1)) else None
		Implicit(tokens(0), defaultVersion).fold(err => Explicit(tokens(0)), identity[Implicit])
	}
	def processSection[T](sections: Map[String, LabelMap], name: String, f: LabelMap => T) =
		process[String,LabelMap,T](sections, name, m => f(m withDefaultValue(None)))
	def process[K,V,T](sections: Map[K,V], name: K, f: V => T): (T, Map[K,V]) = ( f(sections(name)), sections - name)
	def check(map: Map[String, _], label: String): Unit = if(map.isEmpty) () else { throw new BootException(map.keys.mkString("Invalid " + label + "(s): ", ",","")) }
	def check[T](label: String, pair: (T, Map[String, _])): T = { check(pair._2, label); pair._1 }
	def id(map: LabelMap, name: String, default: String): (String, LabelMap) =
		(map.getOrElse(name, None).getOrElse(default), map - name)
	def ids(map: LabelMap, name: String, default: Seq[String]) =
	{
		val result = map(name).map(value => trim(value.split(",")).filter(!_.isEmpty)).getOrElse(default)
		(result, map - name)
	}
	def bool(map: LabelMap, name: String, default: Boolean): (Boolean, LabelMap) =
	{
		val (b, m) = id(map, name, default.toString)
		(b.toBoolean, m)
	}
	def toFile(path: String): File = new File(path)// if the path is relative, it will be resolve by Launch later
	def file(map: LabelMap, name: String, default: File): (File, LabelMap) =
		(map.getOrElse(name, None).map(toFile).getOrElse(default), map  - name)

	def getBoot(m: LabelMap): BootSetup =
	{
		val (dir, m1) = file(m, "directory", toFile("project/boot"))
		val (props, m2) = file(m1, "properties", toFile("project/build.properties"))
		val (search, m3) = getSearch(m2, props)
		val (enableQuick, m4) = bool(m3, "quick-option", false)
		val (promptFill, m5) = bool(m4, "prompt-fill", false)
		val (promptCreate, m6) = id(m5, "prompt-create", "")
		check(m6, "label")
		BootSetup(dir, props, search, promptCreate, enableQuick, promptFill)
	}
	def getLogging(m: LabelMap): Logging = check("label", process(m, "level", getLevel))
	def getLevel(m: Option[String]) = m.map(LogLevel.apply).getOrElse(Logging(LogLevel.Info))
	def getSearch(m: LabelMap, defaultPath: File): (Search, LabelMap) =
		ids(m, "search", Nil) match
		{
			case (Nil, newM) => (Search.none, newM)
			case (Seq(tpe), newM) => (Search(tpe, Seq(defaultPath)), newM)
			case (Seq(tpe, paths @ _ *), newM) => (Search(tpe, paths.map(toFile)), newM)
		}

	def getApplication(m: LabelMap): Application =
	{
		val (org, m1) = id(m, "org", "org.scala-tools.sbt")
		val (name, m2) = id(m1, "name", "sbt")
		val (rev, m3) = getVersion(m2)
		val (main, m4) = id(m3, "class", "xsbt.Main")
		val (components, m5) = ids(m4, "components", Seq("default"))
		val (crossVersioned, m6) = id(m5, "cross-versioned", "true")
		check(m6, "label")
		new Application(org, name, rev, main, components, crossVersioned.toBoolean)
	}
	def getRepositories(m: LabelMap): Seq[Repository] =
	{
		import Repository.{Ivy, Maven, Predefined}
		m.toSeq.map {
			case (key, None) => Predefined(key)
			case (key, Some(value)) =>
				val r = trim(value.split(",",2))
				val url = try { new URL(r(0)) } catch { case e: MalformedURLException => throw new BootException("Invalid URL specified for '" + key + "': " + e.getMessage) }
				if(r.length == 2) Ivy(key, url, r(1)) else Maven(key, url)
		}
	}
	def getAppProperties(m: LabelMap): Seq[AppProperty] =
		for((name, Some(value)) <- m.toSeq) yield
		{
			val map = Map() ++ trim(value.split(",")).map(parsePropertyDefinition(name))
			AppProperty(name)(map.get("quick"), map.get("new"), map.get("fill"))
		}
	def parsePropertyDefinition(name: String)(value: String) = value.split("=",2) match {
		case Array(mode,value) => (mode, parsePropertyValue(name, value)(defineProperty(name)))
		case x => throw new BootException("Invalid property definition '" + x + "' for property '" + name + "'")
	}
	def defineProperty(name: String)(action: String, requiredArg: String, optionalArg: Option[String]) =
		action match
		{
			case "prompt" => PromptProperty(requiredArg, optionalArg)
			case "set" => SetProperty(requiredArg)
			case _ => throw new BootException("Unknown action '" + action + "' for property '"  + name + "'")
		}
	private lazy val propertyPattern = """(.+)\((.*)\)(?:\[(.*)\])?""".r.pattern
	def parsePropertyValue[T](name: String, definition: String)(f: (String, String, Option[String]) => T): T =
	{
		val m = propertyPattern.matcher(definition)
		if(!m.matches()) throw new BootException("Invalid property definition '" + definition + "' for property '" + name + "'")
		val optionalArg = m.group(3)
		f(m.group(1), m.group(2), if(optionalArg eq null) None else Some(optionalArg))
	}
	def trim(s: Array[String]) = s.map(_.trim)

	// line parsing

	def sections = lines ^^ processLines
	type LabelMap = Map[String, Option[String]]
	// section-name -> label -> value
	type SectionMap = Map[String, LabelMap]
	def processLines(lines: List[Line]): SectionMap =
	{
		type State = (SectionMap, Option[String])
		val s: State =
			( ( (Map.empty withDefaultValue(TreeMap.empty[String,Option[String]]), None): State) /: lines ) {
				case (x, Comment) => x
				case ( (map, _), Section(name) ) => (map, Some(name))
				case ( (_, None), l: Labeled ) => throw new BootException("Label " + l.label + " is not in a section")
				case ( (map, s @ Some(section)), l: Labeled ) =>
					val sMap = map(section)
					if( sMap.contains(l.label) ) throw new BootException("Duplicate label '" + l.label + "' in section '" + section + "'")
					else ( map(section) = (sMap(l.label) = l.value), s )
			}
		s._1
	}

	// character parsing
	type Elem = Char
	def lines = (line*)  <~ ws_nl
		def line: Parser[Line] = (ws_nl ~> (comment | section | labeled | failure("Expected comment, start of section, or section entry")) ~! nl ) ^^ { case m ~ _ => m }
		def comment = '#' ~! value ^^ { x => Comment }
		def section = ('[' ~! ws) ~! ID ~! (ws ~! ']' ~! ws) ^^ { case _ ~ i ~ _ => Section(i)}
		def labeled = ID ~! ws ~! ((':' ~! value ^^ { case _ ~ v => v })?) >> {
			case k ~ _ ~ Some(v) =>
				val trimmed = v.trim
				if(trimmed.isEmpty) failure("Value for '" + k + "' was empty") else success(Labeled(k, Some(v.trim)))
			case k ~ _ ~ None => success(Labeled(k, None))
		}

	def ws_nl = string(isWhitespace)
	lazy val ws = string(c => isWhitespace(c) && !isNewline(c))

	def ID = IDword ~! ((ws ~> IDword)*) ^^ { case (x ~ y) => (x :: y).mkString(" ") }
	def IDword = elem("Identifier", isIDStart) ~! string(isIDChar) ^^ { case x ~ xs => x + xs }
	def value = string(c => !isNewline(c))

		def isNewline(c: Char) = c == '\r' || c == '\n'
		def isIDStart(c: Char) = isIDChar(c) && c != '[' && c != '#'
		def isIDChar(c: Char) = !isWhitespace(c) && c != ':' && c != ']' && c != CharSequenceReader.EofCh

	case class string(accept: Char => Boolean) extends Parser[String] with NotNull
	{
		def apply(in: Reader[Char]) =
		{
			val buffer = new StringBuilder
			def fill(in: Reader[Char]): ParseResult[String] =
			{
				if(in.atEnd || !accept(in.first))
					Success(buffer.toString, in)
				else
				{
					buffer += in.first
					fill(in.rest)
				}
			}
			fill(in)
		}
	}
	def nl = new Parser[Unit] with NotNull
	{
		def apply(in: Reader[Char]) =
		{
			if(in.atEnd) Success( (), in)
			else if(isNewline(in.first)) Success( (), in.rest )
			else Failure("Expected end of line", in)
		}
	}
}

sealed trait Line extends NotNull
final case class Labeled(label: String, value: Option[String]) extends Line
final case class Section(name: String) extends Line
object Comment extends Line
