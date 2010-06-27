/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import inc.{Analysis, AnalysisStore, CompileSetup}

	import java.io.File
	import sbinary._
	import Operations.{read, write}
	import DefaultProtocol._
	import JavaIO._

object FileBasedStore
{
	def apply(file: File)(implicit analysisF: Format[Analysis], setupF: Format[CompileSetup]): AnalysisStore = new AnalysisStore {
		def set(analysis: Analysis, setup: CompileSetup): Unit =
			Using.fileOutputStream()(file) { out =>
				write[(Analysis, CompileSetup)](out, (analysis, setup) )
			}

		def get(): (Analysis, CompileSetup) =
			Using.fileInputStream(file) { in =>
				read[(Analysis, CompileSetup)]( in )
			}
	}
}
