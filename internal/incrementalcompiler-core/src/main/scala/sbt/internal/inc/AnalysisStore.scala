/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package internal
package inc

import xsbti.compile.MiniSetup

trait AnalysisStore {
  def set(analysis: Analysis, setup: MiniSetup): Unit
  def get(): Option[(Analysis, MiniSetup)]
}

object AnalysisStore {
  def cached(backing: AnalysisStore): AnalysisStore = new AnalysisStore {
    private var last: Option[(Analysis, MiniSetup)] = None
    def set(analysis: Analysis, setup: MiniSetup): Unit = {
      backing.set(analysis, setup)
      last = Some((analysis, setup))
    }
    def get(): Option[(Analysis, MiniSetup)] =
      {
        if (last.isEmpty)
          last = backing.get()
        last
      }
  }
  def sync(backing: AnalysisStore): AnalysisStore = new AnalysisStore {
    def set(analysis: Analysis, setup: MiniSetup): Unit = synchronized { backing.set(analysis, setup) }
    def get(): Option[(Analysis, MiniSetup)] = synchronized { backing.get() }
  }
}
