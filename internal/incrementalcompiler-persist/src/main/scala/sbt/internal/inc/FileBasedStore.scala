/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package internal
package inc

import java.io.File
import sbt.io.IO
import sbt.internal.io.Using
import xsbti.compile.{ CompileAnalysis, MiniSetup }

object FileBasedStore {
  def apply(file: File): AnalysisStore = new AnalysisStore {
    def set(analysis: CompileAnalysis, setup: MiniSetup): Unit = {
      Using.fileWriter(IO.utf8)(file) { writer => TextAnalysisFormat.write(writer, analysis, setup) }
    }

    def get(): Option[(CompileAnalysis, MiniSetup)] =
      try { Some(getUncaught()) } catch { case _: Exception => None }
    def getUncaught(): (CompileAnalysis, MiniSetup) =
      Using.fileReader(IO.utf8)(file) { reader => TextAnalysisFormat.read(reader) }
  }
}
