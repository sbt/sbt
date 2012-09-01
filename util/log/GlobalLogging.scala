/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

	import java.io.{File, PrintWriter}

final case class GlobalLogging(full: Logger, backed: ConsoleLogger, backing: GlobalLogBacking)
final case class GlobalLogBacking(file: File, last: Option[File], newLogger: (PrintWriter, GlobalLogBacking) => GlobalLogging, newBackingFile: () => File)
{
	def shift(newFile: File) = GlobalLogBacking(newFile, Some(file), newLogger, newBackingFile)
	def shiftNew() = shift(newBackingFile())
	def unshift = GlobalLogBacking(last getOrElse file, None, newLogger, newBackingFile)
}
object GlobalLogBacking
{
	def apply(newLogger: (PrintWriter, GlobalLogBacking) => GlobalLogging, newBackingFile: => File): GlobalLogBacking =
		GlobalLogBacking(newBackingFile, None, newLogger, newBackingFile _)
}
object GlobalLogging
{
	@deprecated("Explicitly specify standard out.", "0.13.0")
	def initial(newLogger: (PrintWriter, GlobalLogBacking) => GlobalLogging, newBackingFile: => File): GlobalLogging =
		initial(newLogger, newBackingFile, ConsoleLogger.systemOut)
	def initial(newLogger: (PrintWriter, GlobalLogBacking) => GlobalLogging, newBackingFile: => File, console: ConsoleOut): GlobalLogging =
	{
		val log = ConsoleLogger(console)
		GlobalLogging(log, log, GlobalLogBacking(newLogger, newBackingFile))
	}
}