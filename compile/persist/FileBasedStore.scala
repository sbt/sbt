/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package inc

	import java.io.{File, IOException}
	import sbinary._
	import Operations.{read, write}
	import DefaultProtocol._
	import JavaIO._

object FileBasedStore
{
	def apply(file: File)(implicit analysisF: Format[Analysis], setupF: Format[CompileSetup]): AnalysisStore = new AnalysisStore {
		def set(analysis: Analysis, setup: CompileSetup): Unit =
			IO.gzipFileOut(file) { out =>
				write[(Analysis, CompileSetup)](out, (analysis, setup) )
			}

		def get(): Option[(Analysis, CompileSetup)] =
			try { Some(getUncaught()) } catch { case _: Exception => None }
		def getUncaught(): (Analysis, CompileSetup) =
			IO.gzipFileIn(file)( in => read[(Analysis, CompileSetup)](in) )
	}
}