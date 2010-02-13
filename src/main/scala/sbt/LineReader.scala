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
	val specificPrefix: String, val scalaVersions: Iterable[String],
	val prefixes: Iterable[String], val taskNames: Iterable[String],
	val propertyNames: Iterable[String], val extra: ExtraCompletions) extends NotNull

trait ExtraCompletions extends NotNull
{
	def names: Iterable[String]
	def completions(name: String): Iterable[String]
}


import jline.{Completor, ConsoleReader}
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
private[sbt] final class LazyJLineReader(historyPath: Option[Path], completor: => Completor, log: Logger) extends JLine
{
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
		cr.addCompletor(new LazyCompletor(completor))
		cr
	}
}
object MainCompletor
{
		import jline.{ArgumentCompletor, MultiCompletor, NullCompletor, SimpleCompletor}

	def apply(completors: Completors): Completor =
	{
			import completors._
			import scala.collection.immutable.TreeSet

		val generalCompletor = simpleCompletor(generalCommands)
		val projectCompletor = simpleArgumentCompletor(projectAction :: Nil, projectNames)

		def propertyCompletor(propertyNames: Iterable[String]) =
			simpleArgumentCompletor(propertyActions, propertyNames)
		def prefixedCompletor(baseCompletor: Completor) =
			singleArgumentCompletor(simpleCompletor(prefixes), baseCompletor)
		def specificCompletor(baseCompletor: Completor) =
		{
			val specific = simpleCompletor(specificPrefix :: Nil) // TODO
			new ArgumentCompletor( Array( specific, simpleCompletor(scalaVersions), baseCompletor ) )
		}
		def extraCompletor(name: String) =
			repeatedArgumentCompletor(simpleCompletor(name :: Nil), new LazyCompletor(simpleCompletor(extra.completions(name))))
		val taskCompletor = simpleCompletor(TreeSet(taskNames.toSeq : _*))
		val extraCompletors = extra.names.map(extraCompletor)
		val baseCompletors = generalCompletor :: taskCompletor :: projectCompletor :: propertyCompletor(propertyNames) :: extraCompletors.toList
		val baseCompletor = new MultiCompletor(baseCompletors.toArray)

		val completor = new MultiCompletor()
		completor.setCompletors( Array(baseCompletor, prefixedCompletor(baseCompletor), specificCompletor(baseCompletor)) )
		completor
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
}
private class LazyCompletor(delegate0: => Completor) extends Completor
{
	private lazy val delegate = delegate0
	def complete(buffer: String, cursor: Int, candidates: java.util.List[_]): Int =
		delegate.complete(buffer, cursor, candidates)
}