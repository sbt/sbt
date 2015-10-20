package sbt
package internal
package inc

import java.io.File

import xsbti.Logger
import xsbti.api.Source
import xsbti.compile.IncOptions
import xsbt.TestAnalyzingCompiler

final class TestIncremental(log: Logger, options: IncOptions) {

  private val incremental: IncrementalCommon =
    if (options.nameHashing)
      new IncrementalNameHashing(log, options)
    else if (options.antStyle)
      new IncrementalAntStyle(log, options)
    else
      new IncrementalDefaultImpl(log, options)

  def changedIncremental[T](lastSources: collection.Set[T], oldAPI: T => Source, newAPI: T => Source): APIChanges[T] =
    incremental.changedIncremental(lastSources, oldAPI, newAPI)

  def invalidateIncremental(previous: Relations, apis: APIs, changes: APIChanges[File], recompiledSources: Set[File], transitive: Boolean): Set[File] =
    incremental.invalidateIncremental(previous, apis, changes, recompiledSources, transitive)

}
