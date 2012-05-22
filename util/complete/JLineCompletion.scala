/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt.complete

	import jline.{CandidateListCompletionHandler,Completor,CompletionHandler,ConsoleReader}
	import scala.annotation.tailrec
	import collection.JavaConversions

object JLineCompletion
{
	def installCustomCompletor(reader: ConsoleReader, parser: Parser[_]): Unit =
		installCustomCompletor(reader)(parserAsCompletor(parser))
	def installCustomCompletor(reader: ConsoleReader)(complete: (String, Int) => (Seq[String], Seq[String])): Unit =
		installCustomCompletor(customCompletor(complete), reader)
	def installCustomCompletor(complete: (ConsoleReader, Int) => Boolean, reader: ConsoleReader): Unit =
	{
		reader.removeCompletor(DummyCompletor)
		reader.addCompletor(DummyCompletor)
		reader.setCompletionHandler(new CustomHandler(complete))
	}

	private[this] final class CustomHandler(completeImpl: (ConsoleReader, Int) => Boolean) extends CompletionHandler
	{
		private[this] var previous: Option[(String,Int)] = None
		private[this] var level: Int = 1
		override def complete(reader: ConsoleReader, candidates: java.util.List[_], position: Int) = {
			val current = Some(bufferSnapshot(reader))
			level = if(current == previous) level + 1 else 1
			previous = current
			try completeImpl(reader, level)
			catch { case e: Exception =>
				reader.printString("\nException occurred while determining completions.")
				e.printStackTrace()
				false
			 }
		}
	}
	
	// always provides dummy completions so that the custom completion handler gets called
	//   (ConsoleReader doesn't call the handler if there aren't any completions)
	//   the custom handler will then throw away the candidates and call the custom function
	private[this] final object DummyCompletor extends Completor
	{
		override def complete(buffer: String, cursor: Int, candidates: java.util.List[_]): Int =
		{
			candidates.asInstanceOf[java.util.List[String]] add "dummy"
			0
		}
	}

	def parserAsCompletor(p: Parser[_]): (String, Int) => (Seq[String], Seq[String]) =
		(str, level) => convertCompletions(Parser.completions(p, str, level))

	def convertCompletions(c: Completions): (Seq[String], Seq[String]) =
	{
		val cs = c.get
		if(cs.isEmpty)
			(Nil, "{invalid input}" :: Nil)
		else
			convertCompletions(cs)
	}
	def convertCompletions(cs: Set[Completion]): (Seq[String], Seq[String]) =
	{
		val (insert, display) =
			( (Set.empty[String], Set.empty[String]) /: cs) { case ( t @ (insert,display), comp) =>
				if(comp.isEmpty) t else (insert + comp.append, appendNonEmpty(display, comp.display.trim))
			}
		(insert.toSeq, display.toSeq.sorted)
	}
	def appendNonEmpty(set: Set[String], add: String) = if(add.isEmpty) set else set + add

	def customCompletor(f: (String, Int) => (Seq[String], Seq[String])): (ConsoleReader, Int) => Boolean =
		(reader, level) => {
			val success = complete(beforeCursor(reader), reader => f(reader, level), reader)
			reader.flushConsole()
			success
		}

	def bufferSnapshot(reader: ConsoleReader): (String, Int) =
	{
		val b = reader.getCursorBuffer
		(b.getBuffer.toString, b.cursor)
	}
	def beforeCursor(reader: ConsoleReader): String =
	{
		val b = reader.getCursorBuffer
		b.getBuffer.substring(0, b.cursor)
	}

	// returns false if there was nothing to insert and nothing to display
	def complete(beforeCursor: String, completions: String => (Seq[String],Seq[String]), reader: ConsoleReader): Boolean =
	{
		val (insert,display) = completions(beforeCursor)
		val common = commonPrefix(insert)
		if(common.isEmpty)
			if(display.isEmpty)
				()
			else
				showCompletions(display, reader)
		else
			appendCompletion(common, reader)

		!(common.isEmpty && display.isEmpty)
	}

	def appendCompletion(common: String, reader: ConsoleReader)
	{
		reader.getCursorBuffer.write(common)
		reader.redrawLine()
	}

	def showCompletions(display: Seq[String], reader: ConsoleReader)
	{
		printCompletions(display, reader)
		reader.drawLine()
	}
	def printCompletions(cs: Seq[String], reader: ConsoleReader): Unit =
	{
		// CandidateListCompletionHandler doesn't print a new line before the prompt
		if(cs.size > reader.getAutoprintThreshhold)
			reader.printNewline()
		CandidateListCompletionHandler.printCandidates(reader, JavaConversions.asJavaList(cs), true)
	}

	def commonPrefix(s: Seq[String]): String = if(s.isEmpty) "" else s reduceLeft commonPrefix
	def commonPrefix(a: String, b: String): String =
	{
		val len = a.length min b.length
		@tailrec def loop(i: Int): Int = if(i >= len) len else if(a(i) != b(i)) i else loop(i+1)
		a.substring(0, loop(0))
	}
}