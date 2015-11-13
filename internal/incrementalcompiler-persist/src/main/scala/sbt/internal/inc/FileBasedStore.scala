/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package internal
package inc

import java.io.File
import sbt.io.IO
import sbt.internal.io.Using
import xsbti.compile.MiniSetup

object FileBasedStore {
  def apply(file: File): AnalysisStore = new AnalysisStore {
    def set(analysis: Analysis, setup: MiniSetup): Unit = {
      Using.fileWriter(IO.utf8)(file) { writer => TextAnalysisFormat.write(writer, analysis, setup) }
    }

    def get(): Option[(Analysis, MiniSetup)] =
      try { Some(getUncaught()) } catch { case _: Exception => None }
    def getUncaught(): (Analysis, MiniSetup) =
      Using.fileReader(IO.utf8)(file) { reader => TextAnalysisFormat.read(reader) }
  }
}
