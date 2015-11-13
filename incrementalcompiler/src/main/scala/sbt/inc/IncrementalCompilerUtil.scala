package sbt
package inc

import xsbti.compile.IncrementalCompiler
import sbt.internal.inc.IncrementalCompilerImpl

object IncrementalCompilerUtil {
  def defaultIncrementalCompiler: IncrementalCompiler =
    new IncrementalCompilerImpl
}
