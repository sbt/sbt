/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package xsbt.boot


import Pre._
import ConfigurationParser._
import java.lang.Character.isWhitespace
import java.io.{BufferedReader, File, FileInputStream, InputStreamReader, Reader, StringReader}
import java.net.{MalformedURLException, URL}
import java.util.regex.{Matcher,Pattern}
import Matcher.quoteReplacement
import scala.collection.immutable.List

object ConfigurationParser
{
	def trim(s: Array[String]) = s.map(_.trim).toList
	def ids(value: String) = trim(value.split(",")).filter(isNonEmpty)
	
	implicit val readIDs = ids _
}
class ConfigurationParser
{
	def apply(file: File): LaunchConfiguration = Using(new InputStreamReader(new FileInputStream(file), "UTF-8"))(apply)
	def apply(s: String): LaunchConfiguration = Using(new StringReader(s))(apply)
	def apply(reader: Reader): LaunchConfiguration = Using(new BufferedReader(reader))(apply)
	private def apply(in: BufferedReader): LaunchConfiguration =
	{
		def readLine(accum: List[Line], index: Int): List[Line] =
		{
			val line = in.readLine()
			if(line eq null) accum.reverse else readLine(ParseLine(line, index) ::: accum, index+1)
		}
		processSections(processLines(readLine(Nil, 0)))
	}
	// section -> configuration instance  processing
	def processSections(sections: SectionMap): LaunchConfiguration =
	{
		val ((scalaVersion, scalaClassifiers), m1) = processSection(sections, "scala", getScala)
		val ((app, appClassifiers), m2) = processSection(m1, "app", getApplication)
		val (repositories, m3) = processSection(m2, "repositories", getRepositories)
		val (boot, m4) = processSection(m3, "boot", getBoot)
		val (logging, m5) = processSection(m4, "log", getLogging)
		val (properties, m6) = processSection(m5, "app-properties", getAppProperties)
		val (ivyHome, m7) = processSection(m6, "ivy", getIvy)
		check(m7, "section")
		val classifiers = Classifiers(scalaClassifiers, appClassifiers)
		new LaunchConfiguration(scalaVersion, IvyOptions(ivyHome, classifiers, repositories), app, boot, logging, properties)
	}
	def getScala(m: LabelMap) =
	{
		val (scalaVersion, m1) = getVersion(m, "Scala version", "scala.version")
		val (scalaClassifiers, m2) = getClassifiers(m1, "Scala classifiers")
		check(m2, "label")
		(scalaVersion, scalaClassifiers)
	}
	def getClassifiers(m: LabelMap, label: String): (Value[List[String]], LabelMap) =
		process(m, "classifiers", processClassifiers(label))
	def processClassifiers(label: String)(value: Option[String]): Value[List[String]] =
		value.map(readValue[List[String]](label)) getOrElse new Explicit(Nil)
		
	def getVersion(m: LabelMap, label: String, defaultName: String): (Value[String], LabelMap) = process(m, "version", processVersion(label, defaultName))
	def processVersion(label: String, defaultName: String)(value: Option[String]): Value[String] =
		value.map(readValue[String](label)).getOrElse(new Implicit(defaultName, None))
		
	def readValue[T](label: String)(implicit read: String => T): String => Value[T] = value =>
	{
		if(isEmpty(value)) error(label + " cannot be empty (omit declaration to use the default)")
		try { parsePropertyValue(label, value)(Value.readImplied[T]) }
		catch { case e: BootException =>  new Explicit(read(value)) }
	}
	def processSection[T](sections: SectionMap, name: String, f: LabelMap => T) =
		process[String,LabelMap,T](sections, name, m => f(m default(x => None)))
	def process[K,V,T](sections: ListMap[K,V], name: K, f: V => T): (T, ListMap[K,V]) = ( f(sections(name)), sections - name)
	def check(map: ListMap[String, _], label: String): Unit = if(map.isEmpty) () else error(map.keys.mkString("Invalid " + label + "(s): ", ",",""))
	def check[T](label: String, pair: (T, ListMap[String, _])): T = { check(pair._2, label); pair._1 }
	def id(map: LabelMap, name: String, default: String): (String, LabelMap) =
		(substituteVariables(orElse(getOrNone(map, name), default)), map - name)
	def getOrNone[K,V](map: ListMap[K,Option[V]], k: K) = orElse(map.get(k), None)
	def ids(map: LabelMap, name: String, default: List[String]) =
	{
		val result = map(name) map ConfigurationParser.ids
		(orElse(result, default), map - name)
	}
	def bool(map: LabelMap, name: String, default: Boolean): (Boolean, LabelMap) =
	{
		val (b, m) = id(map, name, default.toString)
		(toBoolean(b), m)
	}
		
	def toFiles(paths: List[String]): List[File] = paths.map(toFile)
	def toFile(path: String): File = new File(substituteVariables(path).replace('/', File.separatorChar))// if the path is relative, it will be resolved by Launch later
	def file(map: LabelMap, name: String, default: File): (File, LabelMap) =
		(orElse(getOrNone(map, name).map(toFile), default), map - name)

	def getIvy(m: LabelMap): Option[File] =
	{
		val (ivyHome, m1) = file(m, "ivy-home", null) // fix this later
		check(m1, "label")
		if(ivyHome eq null) None else Some(ivyHome)
	}
	def getBoot(m: LabelMap): BootSetup =
	{
		val (dir, m1) = file(m, "directory", toFile("project/boot"))
		val (props, m2) = file(m1, "properties", toFile("project/build.properties"))
		val (search, m3) = getSearch(m2, props)
		val (enableQuick, m4) = bool(m3, "quick-option", false)
		val (promptFill, m5) = bool(m4, "prompt-fill", false)
		val (promptCreate, m6) = id(m5, "prompt-create", "")
		val (lock, m7) = bool(m6, "lock", true)
		check(m7, "label")
		BootSetup(dir, lock, props, search, promptCreate, enableQuick, promptFill)
	}
	def getLogging(m: LabelMap): Logging = check("label", process(m, "level", getLevel))
	def getLevel(m: Option[String]) = m.map(LogLevel.apply).getOrElse(new Logging(LogLevel.Info))
	def getSearch(m: LabelMap, defaultPath: File): (Search, LabelMap) =
		ids(m, "search", Nil) match
		{
			case (Nil, newM) => (Search.none, newM)
			case (tpe :: Nil, newM) => (Search(tpe, List(defaultPath)), newM)
			case (tpe :: paths, newM) => (Search(tpe, toFiles(paths)), newM)
		}

	def getApplication(m: LabelMap): (Application, Value[List[String]]) =
	{
		val (org, m1) = id(m, "org", "org.scala-tools.sbt")
		val (name, m2) = id(m1, "name", "sbt")
		val (rev, m3) = getVersion(m2, name + " version", name + ".version")
		val (main, m4) = id(m3, "class", "xsbt.Main")
		val (components, m5) = ids(m4, "components", List("default"))
		val (crossVersioned, m6) = id(m5, "cross-versioned", "true")
		val (resources, m7) = ids(m6, "resources", Nil)
		val (classifiers, m8) = getClassifiers(m7, "Application classifiers")
		check(m8, "label")
		val classpathExtra = toArray(toFiles(resources))
		val app = new Application(org, name, rev, main, components, toBoolean(crossVersioned), classpathExtra)
		(app, classifiers)
	}
	def getRepositories(m: LabelMap): List[Repository] =
	{
		import Repository.{Ivy, Maven, Predefined}
		m.toList.map {
			case (key, None) => Predefined(key)
			case (key, Some(value)) =>
				val r = trim(value.split(",",2))
				val url = try { new URL(r(0)) } catch { case e: MalformedURLException => error("Invalid URL specified for '" + key + "': " + e.getMessage) }
				if(r.length == 2) Ivy(key, url, r(1)) else Maven(key, url)
		}
	}
	def getAppProperties(m: LabelMap): List[AppProperty] =
		for((name, Some(value)) <- m.toList) yield
		{
			val map = ListMap( trim(value.split(",")).map(parsePropertyDefinition(name)) : _*)
			AppProperty(name)(map.get("quick"), map.get("new"), map.get("fill"))
		}
	def parsePropertyDefinition(name: String)(value: String) = value.split("=",2) match {
		case Array(mode,value) => (mode, parsePropertyValue(name, value)(defineProperty(name)))
		case x => error("Invalid property definition '" + x + "' for property '" + name + "'")
	}
	def defineProperty(name: String)(action: String, requiredArg: String, optionalArg: Option[String]) =
		action match
		{
			case "prompt" => new PromptProperty(requiredArg, optionalArg)
			case "set" => new SetProperty(requiredArg)
			case _ => error("Unknown action '" + action + "' for property '"  + name + "'")
		}
	private[this] lazy val propertyPattern = Pattern.compile("""(.+)\((.*)\)(?:\[(.*)\])?""") // examples: prompt(Version)[1.0] or set(1.0)
	def parsePropertyValue[T](name: String, definition: String)(f: (String, String, Option[String]) => T): T =
	{
		val m = propertyPattern.matcher(definition)
		if(!m.matches()) error("Invalid property definition '" + definition + "' for property '" + name + "'")
		val optionalArg = m.group(3)
		f(m.group(1), m.group(2), if(optionalArg eq null) None else Some(optionalArg))
	}

	type LabelMap = ListMap[String, Option[String]]
	// section-name -> label -> value
	type SectionMap = ListMap[String, LabelMap]
	def processLines(lines: List[Line]): SectionMap =
	{
		type State = (SectionMap, Option[String])
		val s: State =
			( ( (ListMap.empty.default(x => ListMap.empty[String,Option[String]]), None): State) /: lines ) {
				case (x, Comment) => x
				case ( (map, _), s: Section ) => (map, Some(s.name))
				case ( (_, None), l: Labeled ) => error("Label " + l.label + " is not in a section")
				case ( (map, s @ Some(section)), l: Labeled ) =>
					val sMap = map(section)
					if( sMap.contains(l.label) ) error("Duplicate label '" + l.label + "' in section '" + section + "'")
					else ( map(section) = (sMap(l.label) = l.value), s )
			}
		s._1
	}

	private[this] lazy val VarPattern = Pattern.compile("""\$\{([\w.]+)(\-(.+))?\}""")
	def substituteVariables(s: String): String = if(s.indexOf('$') >= 0) substituteVariables0(s) else s
	// scala.util.Regex brought in 30kB, so we code it explicitly
	def substituteVariables0(s: String): String =
	{
		val m = VarPattern.matcher(s)
		val b = new StringBuffer
		while(m.find())
		{
			val key = m.group(1)
			val defined = System.getProperty(key)
			val value =
				if(defined ne null)
					defined
				else
				{
					val default = m.group(3)
					if(default eq null) m.group() else substituteVariables(default)
				}
			m.appendReplacement(b, quoteReplacement(value))
		}
		m.appendTail(b)
		b.toString
	}
}

sealed trait Line
final class Labeled(val label: String, val value: Option[String]) extends Line
final class Section(val name: String) extends Line
object Comment extends Line

class ParseException(val content: String, val line: Int, val col: Int, val msg: String)
	extends BootException( "[" + (line+1) + ", " + (col+1) + "]" + msg + "\n" + content + "\n" + List.make(col," ").mkString + "^" )

object ParseLine
{
	def apply(content: String, line: Int) =
	{
		def error(col: Int, msg: String) = throw new ParseException(content, line, col, msg)
		def check(condition: Boolean)(col: Int, msg: String) = if(condition) () else error(col, msg)

		val trimmed = trimLeading(content)
		val offset = content.length - trimmed.length

		def section =
		{
			val closing = trimmed.indexOf(']', 1)
			check(closing > 0)(content.length, "Expected ']', found end of line")
			val extra = trimmed.substring(closing+1)
			val trimmedExtra = trimLeading(extra)
			check(isEmpty(trimmedExtra))(content.length - trimmedExtra.length, "Expected end of line, found '" + extra + "'")
			new Section(trimmed.substring(1,closing).trim)
		}
		def labeled =
		{
			trimmed.split(":",2) match {
				case Array(label, value) =>
					val trimmedValue = value.trim
					check(isNonEmpty(trimmedValue))(content.indexOf(':'), "Value for '" + label + "' was empty")
					 new Labeled(label, Some(trimmedValue))
				case x => new Labeled(x.mkString, None)
			}
		}
		
		if(isEmpty(trimmed)) Nil
		else
		{
			val processed =
				trimmed.charAt(0) match
				{
					case '#' => Comment
					case '[' => section
					case _ => labeled
				}
			processed :: Nil
		}
	}
}