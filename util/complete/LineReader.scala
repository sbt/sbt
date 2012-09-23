/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package sbt

	import jline.{ConsoleReader, History}
	import java.io.{File, InputStream, PrintWriter}
	import complete.Parser
	import java.util.concurrent.atomic.AtomicBoolean

abstract class JLine extends LineReader
{
	protected[this] val handleCONT: Boolean
	protected[this] val reader: ConsoleReader
	/** Is the input stream at EOF? Compensates for absent EOF detection in JLine's UnsupportedTerminal. */
	protected[this] val inputEof = new AtomicBoolean(false)
	protected[this] val historyPath: Option[File]

	def readLine(prompt: String, mask: Option[Char] = None) = JLine.withJLine { unsynchronizedReadLine(prompt, mask) }

	private[this] def unsynchronizedReadLine(prompt: String, mask: Option[Char]) =
		readLineWithHistory(prompt, mask) match
		{
			case null => None
			case x => Some(x.trim)
		}

	private[this] def readLineWithHistory(prompt: String, mask: Option[Char]): String =
		historyPath match
		{
			case None => readLineDirect(prompt, mask)
			case Some(file) =>
				val h = reader.getHistory
				JLine.loadHistory(h, file)
				try { readLineDirect(prompt, mask) }
				finally { JLine.saveHistory(h, file) }
		}

	private[this] def readLineDirect(prompt: String, mask: Option[Char]): String =
		if(handleCONT)
			Signals.withHandler(() => resume(), signal = Signals.CONT)( () => readLineDirectRaw(prompt, mask) )
		else
			readLineDirectRaw(prompt, mask)
	private[this] def readLineDirectRaw(prompt: String, mask: Option[Char]): String =
	{
		val line = mask match {
			case Some(m) => reader.readLine(prompt, m)
			case None => reader.readLine(prompt)
		}
		if (inputEof.get) null else line
	}

	private[this] def resume()
	{
		jline.Terminal.resetTerminal
		JLine.terminal.disableEcho()
		reader.drawLine()
		reader.flushConsole()
	}
}
private object JLine
{
	// When calling this, ensure that enableEcho has been or will be called.
	//  getTerminal will initialize the terminal to disable echo.
	private def terminal = jline.Terminal.getTerminal
	private def withTerminal[T](f: jline.Terminal => T): T =
		synchronized
		{
			val t = terminal
			t.synchronized { f(t) }
		}
	/** For accessing the JLine Terminal object.
	* This ensures synchronized access as well as re-enabling echo after getting the Terminal. */
	def usingTerminal[T](f: jline.Terminal => T): T =
		withTerminal { t =>
			t.enableEcho()
			f(t)
		}
	def createReader() =
		usingTerminal { t =>
			val cr = new ConsoleReader
			cr.setBellEnabled(false)
			cr
		}
	def withJLine[T](action: => T): T =
		withTerminal { t =>
			t.disableEcho()
			try { action }
			finally { t.enableEcho() }
		}
	private[sbt] def loadHistory(h: History, file: File)
	{
		h.setMaxSize(MaxHistorySize)
		if(file.isFile) IO.reader(file)( h.load )
	}
	private[sbt] def saveHistory(h: History, file: File): Unit =
		Using.fileWriter()(file) { writer =>
			val out = new PrintWriter(writer, false)
			h.setOutput(out)
			h.flushBuffer()
			out.close()
			h.setOutput(null)
		}

	def simple(historyPath: Option[File], handleCONT: Boolean = HandleCONT): SimpleReader = new SimpleReader(historyPath, handleCONT)
	val MaxHistorySize = 500
	val HandleCONT = !java.lang.Boolean.getBoolean("sbt.disable.cont") && Signals.supported(Signals.CONT)
}

trait LineReader
{
	def readLine(prompt: String, mask: Option[Char] = None): Option[String]
}
final class FullReader(val historyPath: Option[File], complete: Parser[_], val handleCONT: Boolean = JLine.HandleCONT) extends JLine
{
	protected[this] val reader =
	{
		val cr = new ConsoleReader
		if (!cr.getTerminal.isSupported) {
			val input = cr.getInput
			cr.setInput(new InputStream {
				def read(): Int = {
					val c = input.read()
					if (c == -1) inputEof.set(true)
					c
				}
			})
		}
		cr.setBellEnabled(false)
		sbt.complete.JLineCompletion.installCustomCompletor(cr, complete)
		cr
	}
}

class SimpleReader private[sbt] (val historyPath: Option[File], val handleCONT: Boolean) extends JLine
{
	protected[this] val reader = JLine.createReader()
}
object SimpleReader extends SimpleReader(None, JLine.HandleCONT)

