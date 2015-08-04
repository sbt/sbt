/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package inc

import java.io.File

object FileBasedStore {
  def apply(file: File): AnalysisStore = new AnalysisStore {
    def set(analysis: Analysis, setup: CompileSetup): Unit = {
      Using.fileWriter(IO.utf8)(file) { writer => TextAnalysisFormat.write(writer, analysis, setup) }
    }

    def get(): Option[(Analysis, CompileSetup)] =
      try { Some(getUncaught()) } catch { case _: Exception => None }
    def getUncaught(): (Analysis, CompileSetup) =
      Using.fileReader(IO.utf8)(file) { reader => TextAnalysisFormat.read(reader) }
  }
}
