/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package xsbt.boot

import jline.console.ConsoleReader
abstract class JLine
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
	def terminal = jline.TerminalFactory.get
	def createReader() =
		terminal.synchronized
		{
			val cr = new ConsoleReader
			terminal.setEchoEnabled(true)
			cr.setBellEnabled(false)
			cr
		}
	def withJLine[T](action: => T): T =
	{
		val t = terminal
		t.synchronized
		{
			t.setEchoEnabled(false)
			try { action }
			finally { t.setEchoEnabled(true) }
		}
	}
}
object SimpleReader extends JLine
{
	protected[this] val reader = JLine.createReader()
}