package sbt

	import java.io.File

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
		in.map { case (a, b) => ("  " + fill(a, width) + sep + b) }
	}
	def fill(s: String, size: Int)  =  s + " " * math.max(size - s.length, 0)

	def withAttribute[T](s: State, key: AttributeKey[T], ifMissing: String)(f: T => State): State =
		(s get key) match {
			case None => s.log.error(ifMissing); s.fail
			case Some(nav) => f(nav)
		}
}