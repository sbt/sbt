/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package sbt

trait LineReader extends NotNull
{
	def readLine(prompt: String): Option[String]
}
class Completors(val projectAction: String, val projectNames: Iterable[String],
	val generalCommands: Iterable[String], val propertyActions: Iterable[String],
	val prefixes: Iterable[String]) extends NotNull
import jline.ConsoleReader
abstract class JLine extends LineReader
{
	protected[this] val reader: ConsoleReader
	def readLine(prompt: String) = JLine.withJLine { unsynchronizedReadLine(prompt) }
	private[this] def unsynchronizedReadLine(prompt: String) =
		reader.readLine(prompt) match
		{
			case null => None
			case x => Some(x.trim)
		}
}
private object JLine
{
	def terminal = jline.Terminal.getTerminal
	def createReader() =
		terminal.synchronized
		{
			val cr = new ConsoleReader
			terminal.enableEcho()
			cr.setBellEnabled(false)
			cr
		}
	def withJLine[T](action: => T): T =
	{
		val t = terminal
		t.synchronized
		{
			t.disableEcho()
			try { action }
			finally { t.enableEcho() }
		}
	}
}
object SimpleReader extends JLine
{
	protected[this] val reader = JLine.createReader()
}
class JLineReader(historyPath: Option[Path], completors: Completors, log: Logger) extends JLine
{
	import completors._
	import jline.{ArgumentCompletor, Completor, MultiCompletor, NullCompletor, SimpleCompletor}
	
	private val generalCompletor = simpleCompletor(generalCommands)
	private val projectCompletor = simpleArgumentCompletor(projectAction :: Nil, projectNames)
		
	private val completor = new MultiCompletor()
	
	protected[this] val reader =
	{
		val cr = new ConsoleReader
		cr.setBellEnabled(false)
		for(historyLocation <- historyPath)
		{
			val historyFile = historyLocation.asFile
			Control.trapAndLog(log)
			{
				historyFile.getParentFile.mkdirs()
				cr.getHistory.setHistoryFile(historyFile)
			}
		}
		cr.addCompletor(completor)
		cr
	}
	
	/** Used for a single argument so that the argument can have spaces in it.*/
	object SingleArgumentDelimiter extends ArgumentCompletor.AbstractArgumentDelimiter
	{
		def isDelimiterChar(buffer: String, pos: Int) =
			(buffer.charAt(pos) == ' ') && buffer.substring(0, pos).trim.indexOf(' ') == -1
	}
	
	private def simpleCompletor(completions: Iterable[String]) = new SimpleCompletor(completions.toList.toArray)
	private def simpleArgumentCompletor(first: Iterable[String], second: Iterable[String]) =
		singleArgumentCompletor(simpleCompletor(first), simpleCompletor(second))
	private def singleArgumentCompletor(first: Completor, second: Completor) =
	{
		val completors = Array(first, second, new NullCompletor)
		val c = new ArgumentCompletor(completors, SingleArgumentDelimiter)
		c.setStrict(true)
		c
	}
	private def repeatedArgumentCompletor(first: Completor, repeat: Completor) =
	{
		val c = new ArgumentCompletor(Array(first, repeat))
		c.setStrict(true)
		c
	}
	
	private def propertyCompletor(propertyNames: Iterable[String]) =
		simpleArgumentCompletor(propertyActions, propertyNames)
	private def prefixedCompletor(baseCompletor: Completor) =
		singleArgumentCompletor(simpleCompletor(prefixes.toList.toArray), baseCompletor)
	def setVariableCompletions(taskNames: Iterable[String], propertyNames: Iterable[String], extra: Iterable[(String, Iterable[String])] )
	{
		import scala.collection.immutable.TreeSet
		val taskCompletor = simpleCompletor(TreeSet(taskNames.toSeq : _*))
		val extraCompletors = for( (first, repeat) <- extra) yield repeatedArgumentCompletor(simpleCompletor(first :: Nil), simpleCompletor(repeat))
		val baseCompletors = generalCompletor :: taskCompletor :: projectCompletor :: propertyCompletor(propertyNames) :: extraCompletors.toList
		val baseCompletor = new MultiCompletor(baseCompletors.toArray)
		completor.setCompletors( Array(baseCompletor, prefixedCompletor(baseCompletor)) )
	}
}