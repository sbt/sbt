/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package sbt
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
	def resetTerminal() = withTerminal { _ => jline.Terminal.resetTerminal }
	private def withTerminal[T](f: jline.Terminal => T): T =
		synchronized
		{
			val t = terminal
			t.synchronized { f(t) }
		}
	def createReader() =
		withTerminal { t =>
			val cr = new ConsoleReader
			t.enableEcho()
			cr.setBellEnabled(false)
			cr
		}
	def withJLine[T](action: => T): T =
		withTerminal { t =>
			t.disableEcho()
			try { action }
			finally { t.enableEcho() }
		}
	private[sbt] def initializeHistory(cr: ConsoleReader, historyPath: Option[Path]): Unit =
		for(historyLocation <- historyPath)
		{
			val historyFile = historyLocation.asFile
			ErrorHandling.wideConvert
			{
				historyFile.getParentFile.mkdirs()
				val history = cr.getHistory
				history.setMaxSize(MaxHistorySize)
				history.setHistoryFile(historyFile)
			}
		}
	def simple(historyPath: Option[Path]): SimpleReader = new SimpleReader(historyPath)
	val MaxHistorySize = 500
}

trait LineReader extends NotNull
{
	def readLine(prompt: String): Option[String]
}
private[sbt] final class LazyJLineReader(historyPath: Option[Path], completor: => Completor) extends JLine
{
	protected[this] val reader =
	{
		val cr = new ConsoleReader
		cr.setBellEnabled(false)
		JLine.initializeHistory(cr, historyPath)
		cr.addCompletor(new LazyCompletor(completor))
		cr
	}
}
private class LazyCompletor(delegate0: => Completor) extends Completor
{
	private lazy val delegate = delegate0
	def complete(buffer: String, cursor: Int, candidates: java.util.List[_]): Int =
		delegate.complete(buffer, cursor, candidates)
}

class SimpleReader private[sbt] (historyPath: Option[Path]) extends JLine
{
	protected[this] val reader = JLine.createReader()
	JLine.initializeHistory(reader, historyPath)
}
object SimpleReader extends JLine
{
	protected[this] val reader = JLine.createReader()
}