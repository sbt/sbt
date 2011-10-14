/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package sbt

	import jline.{Completor, ConsoleReader}
	import java.io.File
	import complete.Parser
	
abstract class JLine extends LineReader
{
	protected[this] val reader: ConsoleReader
	def readLine(prompt: String, mask: Option[Char] = None) = JLine.withJLine { unsynchronizedReadLine(prompt, mask) }
	private[this] def unsynchronizedReadLine(prompt: String, mask: Option[Char]) =
		(mask match {
			case Some(m) => reader.readLine(prompt, m)
			case None => reader.readLine(prompt)
		}) match
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
	private[sbt] def initializeHistory(cr: ConsoleReader, historyPath: Option[File]): Unit =
		for(historyLocation <- historyPath)
		{
			val historyFile = historyLocation.getAbsoluteFile
			ErrorHandling.wideConvert
			{
				historyFile.getParentFile.mkdirs()
				val history = cr.getHistory
				history.setMaxSize(MaxHistorySize)
				history.setHistoryFile(historyFile)
			}
		}
	def simple(historyPath: Option[File]): SimpleReader = new SimpleReader(historyPath)
	val MaxHistorySize = 500
}

trait LineReader
{
	def readLine(prompt: String, mask: Option[Char] = None): Option[String]
}
final class FullReader(historyPath: Option[File], complete: Parser[_]) extends JLine
{
	protected[this] val reader =
	{
		val cr = new ConsoleReader
		cr.setBellEnabled(false)
		JLine.initializeHistory(cr, historyPath)
		sbt.complete.JLineCompletion.installCustomCompletor(cr, complete)
		cr
	}
}

class SimpleReader private[sbt] (historyPath: Option[File]) extends JLine
{
	protected[this] val reader = JLine.createReader()
	JLine.initializeHistory(reader, historyPath)
}
object SimpleReader extends JLine
{
	protected[this] val reader = JLine.createReader()
}
