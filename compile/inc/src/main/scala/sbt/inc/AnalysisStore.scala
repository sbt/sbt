/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

trait AnalysisStore {
  def set(analysis: Analysis, setup: CompileSetup): Unit
  def get(): Option[(Analysis, CompileSetup)]
}

object AnalysisStore {
  def cached(backing: AnalysisStore): AnalysisStore = new AnalysisStore {
    private var last: Option[(Analysis, CompileSetup)] = None
    def set(analysis: Analysis, setup: CompileSetup): Unit = {
      backing.set(analysis, setup)
      last = Some((analysis, setup))
    }
    def get(): Option[(Analysis, CompileSetup)] =
      {
        if (last.isEmpty)
          last = backing.get()
        last
      }
  }
  def sync(backing: AnalysisStore): AnalysisStore = new AnalysisStore {
    def set(analysis: Analysis, setup: CompileSetup): Unit = synchronized { backing.set(analysis, setup) }
    def get(): Option[(Analysis, CompileSetup)] = synchronized { backing.get() }
  }
}
