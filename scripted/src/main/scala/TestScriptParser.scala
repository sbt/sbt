/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */

package sbt.test

import java.io.{BufferedReader, File, InputStreamReader}

/*
statement*
statement ::= ('$' | '>') word+ '[' word ']'
word ::= [^ \[\]]+
comment ::= '#' [^ \n\r]* ('\n' | '\r' | eof)
*/
import scala.util.parsing.combinator._
import scala.util.parsing.input.Positional

import TestScriptParser._
private class TestScriptParser(baseDirectory: File, log: Logger) extends RegexParsers with NotNull
{
	type Statement = Project => Either[String, ReloadProject]
	type PStatement = Statement with Positional
	
	private def evaluateList(list: List[PStatement])(p: Project): WithProjectResult[Unit] =
		list match
		{
			case Nil => ValueResult(())
			case head :: tail =>
				head(p) match
				{
					case Left(msg) => new ErrorResult(msg)
					case Right(reload) => ContinueResult(p =>evaluateList(tail)(p), reload)
				}
		}

	def script: Parser[Project => WithProjectResult[Unit]] = rep1(space ~> statement  <~ space) ^^ evaluateList
	def statement: Parser[PStatement] =
		positioned
		{
			(StartRegex ~! rep1(word) ~! "[" ~! word ~! "]") ^^
			{
				case start ~ command ~ open ~ result ~ close =>
					val successExpected = result.toLowerCase == SuccessLiteral.toLowerCase
					new Statement with Positional
					{ selfPositional =>
						def apply(p: Project) =
						{
							val result =
								try
								{
									start match
									{
										case CommandStart => evaluateCommand(command, successExpected, selfPositional)(p)
										case ActionStart => evaluateAction(command, successExpected)(p).toLeft(NoReload)
									}
								}
								catch
								{
									case e: Exception =>
										log.trace(e)
										Left(e.toString)
								}
							result.left.map(message => linePrefix(this) + message)
						}
					}
			}
		}
	private def linePrefix(p: Positional) = "{line " + p.pos.line + "} "
	def space = """(\s+|(\#[^\n\r]*))*""".r
	def word: Parser[String] =  ("\'" ~> "[^'\n\r]*".r <~ "\'")  |  ("\"" ~> "[^\"\n\r]*".r <~ "\"")  |  WordRegex
	def parse(scriptFile: File): Either[String, Project => WithProjectResult[Unit]] =
	{
		def parseReader(reader: java.io.Reader) =
			parseAll(script, reader) match
			{
				case Success(result, next) => Right(result)
				case err: NoSuccess =>
				{
					val pos = err.next.pos
					Left("Could not parse test script '" + scriptFile.getCanonicalPath + 
					"' (" + pos.line + "," + pos.column + "): " + err.msg)
				}
			}
		FileUtilities.readValue(scriptFile, log)(parseReader)
	}
	
	private def scriptError(message: String): Some[String] = Some("Test script error: " + message)
	private def wrongArguments(commandName: String, args: List[String]): Some[String] =
		scriptError("Command '" + commandName + "' does not accept arguments (found '" + spacedString(args) + "').")
	private def wrongArguments(commandName: String, requiredArgs: String, args: List[String]): Some[String] = 
		scriptError("Wrong number of arguments to " + commandName + " command.  " + requiredArgs + " required, found: '" + spacedString(args) + "'.")
	private def evaluateCommand(command: List[String], successExpected: Boolean, position: Positional)(project: Project): Either[String, ReloadProject] =
	{
		command match
		{
			case "reload" :: Nil => Right(if(successExpected) new ReloadSuccessExpected(linePrefix(position)) else ReloadErrorExpected)
			case x => evaluateCommandNoReload(x, successExpected)(project).toLeft(NoReload)
		}
	}
	private def evaluateCommandNoReload(command: List[String], successExpected: Boolean)(project: Project): Option[String] =
	{
		evaluate(successExpected, "Command '" + command.firstOption.getOrElse("") + "'", project)
		{
			command match
			{
				case Nil => scriptError("No command specified.")
				case "touch" :: paths => touch(paths, project)
				case "delete" :: paths => delete(paths, project)
				case "mkdir" :: paths => makeDirectories(paths, project)
				case "copy-file" :: from :: to :: Nil => copyFile(from, to, project)
				case "copy-file" :: args => wrongArguments("copy-file", "Two paths", args)
				case "sync" :: from :: to :: Nil => sync(from, to, project)
				case "sync" :: args => wrongArguments("sync", "Two directory paths", args)
				case "copy" :: paths => copy(paths, project)
				case "exists" :: paths => exists(paths, project)
				case "absent" :: paths => absent(paths, project)
				case "pause" :: Nil => readLine("Press enter to continue. "); println(); None
				case "pause" :: args => wrongArguments("pause", args)
				case "newer" :: a :: b :: Nil => newer(a, b, project)
				case "newer" :: args => wrongArguments("newer", "Two paths", args)
				case "sleep" :: time :: Nil => trap("Error while sleeping:") { Thread.sleep(time.toLong) }
				case "sleep" :: args => wrongArguments("sleep", "Time in milliseconds", args)
				case "exec" :: command :: args => execute(command, args, project)
				case "exec" :: other => wrongArguments("exec", "Command and arguments", other)
				case "reload" :: args => wrongArguments("reload", args)
				case unknown :: arguments => scriptError("Unknown command " + unknown)
			}
		}
	}
	private def foreachBufferedLogger(project: Project)(f: BufferedLogger => Unit)
	{
		project.topologicalSort.foreach(p => p.log match { case buffered: BufferedLogger => f(buffered); case _ => () })
	}
	private def evaluate(successExpected: Boolean, label: String, project: Project)(body: => Option[String]): Option[String] =
	{
		def startRecordingLog() { foreachBufferedLogger(project)(_.startRecording()) }
		def playLog() { foreachBufferedLogger(project)(_.playAll()) }
		def stopLog() { foreachBufferedLogger(project)(_.stop()) }
		
		startRecordingLog()
		val result =
			body match
			{
				case None =>
					if(successExpected) None
					else
					{
						playLog()
						Some(label + " succeeded (expected failure).")
					}
				case Some(failure) =>
					if(successExpected)
					{
						playLog()
						Some(label + " failed (expected success): " + failure)
					}
					else None
			}
		stopLog()
		result
	}
	private def evaluateAction(action: List[String], successExpected: Boolean)(project: Project): Option[String] =
	{
		def actionToString = action.mkString(" ")
		action match
		{
			case Nil => scriptError("No action specified.")
			case head :: Nil if project.taskNames.toSeq.contains(head)=>
					evaluate(successExpected, "Action '" + actionToString + "'", project)(project.act(head))
			case head :: tail =>
				evaluate(successExpected, "Method '" + actionToString + "'", project)(project.call(head, tail.toArray))
		}
	}
	private def spacedString[T](l: Seq[T]) = l.mkString(" ")
	private def wrap(result: Option[String]) = result.flatMap(scriptError)
	private def trap(errorPrefix: String)(action: => Unit) = wrap( Control.trapUnit(errorPrefix, log) { action; None } )
	
	private def fromStrings(paths: List[String], project: Project) = paths.map(path => fromString(path, project))
	private def fromString(path: String, project: Project) = Path.fromString(project.info.projectPath, path)
	private def touch(paths: List[String], project: Project) =
		if(paths.isEmpty)
			scriptError("No paths specified for touch command.")
		else
			wrap(lazyFold(paths) { path => FileUtilities.touch(fromString(path, project), log) })
		
	private def delete(paths: List[String], project: Project) =
		if(paths.isEmpty)
			scriptError("No paths specified for delete command.")
		else
			wrap(FileUtilities.clean(fromStrings(paths, project), true, log))
	private def sync(from: String, to: String, project: Project) =
		wrap(FileUtilities.sync(fromString(from, project), fromString(to, project), log))
	private def copyFile(from: String, to: String, project: Project) =
		wrap(FileUtilities.copyFile(fromString(from, project), fromString(to, project), log))
	private def copy(paths: List[String], project: Project) =
		paths match
		{
			case Nil => scriptError("No paths specified for copy command.")
			case path :: Nil => scriptError("No destination specified for copy command.")
			case _ =>
				val mapped = fromStrings(paths, project).toArray
				val last = mapped.length - 1
				wrap(FileUtilities.copy(mapped.take(last), mapped(last), log).left.toOption)
		}
	private def makeDirectories(paths: List[String], project: Project) =
		fromStrings(paths, project) match
		{
			case Nil => scriptError("No paths specified for mkdir command.")
			case p => FileUtilities.createDirectories(p, project.log)
		}
	private def newer(a: String, b: String, project: Project) =
		trap("Error testing if '" + a + "' is newer than '" + b + "'")
		{
			val pathA = fromString(a, project)
			val pathB = fromString(b, project)
			pathA.exists && (!pathB.exists || pathA.lastModified > pathB.lastModified)
		}
	private def exists(paths: List[String], project: Project) =
		fromStrings(paths, project).filter(!_.exists) match
		{
			case Nil => None
			case x => Some("File(s) did not exist: " + x.mkString("[ ", " , ", " ]"))
		}
	private def absent(paths: List[String], project: Project) =
		fromStrings(paths, project).filter(_.exists) match
		{
			case Nil => None
			case x => Some("File(s) existed: " + x.mkString("[ ", " , ", " ]"))
		}
	private def execute(command: String, args: List[String], project: Project) =
	{
		if(command.trim.isEmpty)
			Some("Command was empty.")
		else
		{
			Control.trapUnit("Error running command: ", project.log)
			{
				val builder = new java.lang.ProcessBuilder((command :: args).toArray :  _*).directory(project.info.projectDirectory)
				val exitValue = Process(builder) ! log
				if(exitValue == 0)
					None
				else
					Some("Nonzero exit value (" + exitValue + ")")
			}
		}
	}
}
private object TestScriptParser
{
	val SuccessLiteral = "success"
	val Failure = "error"
	val CommandStart = "$"
	val ActionStart = ">"
	val WordRegex = """[^ \[\]\s'\"][^ \[\]\s]*""".r
	val StartRegex = ("[" + CommandStart + ActionStart + "]").r
	
	final def lazyFold[T](list: List[T])(f: T => Option[String]): Option[String] =
		list match
		{
			case Nil => None
			case head :: tail =>
				f(head) match
				{
					case None => lazyFold(tail)(f)
					case x => x
				}
		}
}
