/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt.parse

	import jline.{CandidateListCompletionHandler,Completor,CompletionHandler,ConsoleReader}
	import scala.annotation.tailrec
	import collection.JavaConversions

object JLineCompletion
{
	def installCustomCompletor(reader: ConsoleReader, parser: Parser[_]): Unit =
		installCustomCompletor(parserAsCompletor(parser), reader)
	def installCustomCompletor(reader: ConsoleReader)(complete: String => (Seq[String], Seq[String])): Unit =
		installCustomCompletor(customCompletor(complete), reader)
	def installCustomCompletor(complete: ConsoleReader => Boolean, reader: ConsoleReader): Unit =
	{
		reader.removeCompletor(DummyCompletor)
		reader.addCompletor(DummyCompletor)
		reader.setCompletionHandler(new CustomHandler(complete))
	}

	private[this] final class CustomHandler(completeImpl: ConsoleReader => Boolean) extends CompletionHandler
	{
		override def complete(reader: ConsoleReader, candidates: java.util.List[_], position: Int) = completeImpl(reader)
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

	def parserAsCompletor(p: Parser[_]): ConsoleReader => Boolean =
		customCompletor(str => convertCompletions(Parser.completions(p, str)))
	def convertCompletions(c: Completions): (Seq[String], Seq[String]) =
	{
		( (Seq[String](), Seq[String]()) /: c.get) { case ( t @ (insert,display), comp) =>
			if(comp.isEmpty) t else (insert :+ comp.append, insert :+ comp.display)
		}
	}
	
	def customCompletor(f: String => (Seq[String], Seq[String])): ConsoleReader => Boolean =
		reader => {
			val success = complete(beforeCursor(reader), f, reader, false)
			reader.flushConsole()
			success
		}

	def beforeCursor(reader: ConsoleReader): String =
	{
		val b = reader.getCursorBuffer
		b.getBuffer.substring(0, b.cursor)
	}

	def complete(beforeCursor: String, completions: String => (Seq[String],Seq[String]), reader: ConsoleReader, inserted: Boolean): Boolean =
	{
		val (insert,display) = completions(beforeCursor)
		if(insert.isEmpty)
			inserted
		else
		{
			lazy val common = commonPrefix(insert)
			if(inserted || common.isEmpty)
			{
				showCompletions(display, reader)
				reader.drawLine()
				true
			}
			else
			{
				reader.getCursorBuffer.write(common)
				reader.redrawLine()
				complete(beforeCursor + common, completions, reader, true)
			}
		}
	}
	def showCompletions(cs: Seq[String], reader: ConsoleReader): Unit =
		if(cs.isEmpty) () else CandidateListCompletionHandler.printCandidates(reader, JavaConversions.asJavaList(cs), true)

	def commonPrefix(s: Seq[String]): String = if(s.isEmpty) "" else s reduceLeft commonPrefix
	def commonPrefix(a: String, b: String): String =
	{
		val len = a.length min b.length
		@tailrec def loop(i: Int): Int = if(i >= len) len else if(a(i) != b(i)) i else loop(i+1)
		a.substring(0, loop(0))
	}
}