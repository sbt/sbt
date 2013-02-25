package sbt

	import java.io.File
	import java.util.regex.{Pattern, PatternSyntaxException}

	import complete.Parser
	import complete.DefaultParsers._

object CommandUtil
{
	def readLines(files: Seq[File]): Seq[String] = files flatMap (line => IO.readLines(line)) flatMap processLine
	def processLine(s: String) = { val trimmed = s.trim; if(ignoreLine(trimmed)) None else Some(trimmed) }
	def ignoreLine(s: String) = s.isEmpty || s.startsWith("#")

	private def canRead = (_: File).canRead
	def notReadable(files: Seq[File]): Seq[File] = files filterNot canRead
	def readable(files: Seq[File]): Seq[File] = files filter canRead

	// slightly better fallback in case of older launcher
	def bootDirectory(state: State): File =
		try { state.configuration.provider.scalaProvider.launcher.bootDirectory }
		catch { case e: NoSuchMethodError => new File(".").getAbsoluteFile }

	def aligned(pre: String, sep: String, in: Seq[(String, String)]): Seq[String] =
	{
		val width = in.map(_._1.length).max
		in.map { case (a, b) => (pre + fill(a, width) + sep + b) }
	}
	def fill(s: String, size: Int)  =  s + " " * math.max(size - s.length, 0)

	def withAttribute[T](s: State, key: AttributeKey[T], ifMissing: String)(f: T => State): State =
		(s get key) match {
			case None => s.log.error(ifMissing); s.fail
			case Some(nav) => f(nav)
		}

	def singleArgument(exampleStrings: Set[String]): Parser[String] =
	{
		val arg = (NotSpaceClass ~ any.*) map { case (ns, s) => (ns +: s).mkString }
		token(Space) ~> token( arg examples exampleStrings  )
	}
	def detail(selected: String, detailMap: Map[String, String]): String =
		detailMap.get(selected) match
		{
			case Some(exactDetail) => exactDetail
			case None => try {
				val details = searchHelp(selected, detailMap)
				if(details.isEmpty)
					"No matches for regular expression '" + selected + "'."
				else
					layoutDetails(details)
			} catch {
				case pse: PatternSyntaxException => sys.error("Invalid regular expression (java.util.regex syntax).\n" + pse.getMessage)
			}
		}
	def searchHelp(selected: String, detailMap: Map[String, String]): Map[String, String] =
	{
		val pattern = Pattern.compile(selected, HelpPatternFlags)
		detailMap flatMap { case (k,v) =>
			val contentMatches = Highlight.showMatches(pattern)(v)
			val keyMatches = Highlight.showMatches(pattern)(k)
			val keyString = Highlight.bold(keyMatches getOrElse k)
			val contentString = contentMatches getOrElse v
			if(keyMatches.isDefined || contentMatches.isDefined)
				(keyString, contentString) :: Nil
			else
				Nil
		}
	}
	def layoutDetails(details: Map[String,String]): String =
		details.map { case (k,v) => k + "\n\n  " + v  } mkString("\n", "\n\n", "\n")

	final val HelpPatternFlags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE

}