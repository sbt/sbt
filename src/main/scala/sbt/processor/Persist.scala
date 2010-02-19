/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt.processor

import java.io.File

import xsbt.FileUtilities.{readLines, write}

// lock file should be for synchronizing access to the persisted files
class Persist(val lock: xsbti.GlobalLock, lockFile: File, defParser: DefinitionParser) extends Persisting
{
	private def withDefinitionsLock[T](f: => T): T = lock(lockFile,Callable(f))
	
	def save(file: File)(definitions: Iterable[Definition])
	{
		val lines = definitions.mkString(LineSeparator)
		withDefinitionsLock { write(file, lines) }
	}
	def load(file: File): Seq[Definition] =
	{
		def parseLine(line: String) = defParser.parseDefinition(line).toList
		withDefinitionsLock { if(file.exists) readLines(file) else Nil } flatMap(parseLine)
	}
	private final val LineSeparator = System.getProperty("line.separator", "\n")
}
