/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import xsbti.VirtualFileRef
import xsbti.compile.{ APIChange, InitialChanges, RunProfiler }

class DefaultRunProfiler(profilers: Seq[RunProfiler]) extends RunProfiler {
  override def timeCompilation(startNanos: Long, durationNanos: Long): Unit =
    profilers.foreach(_.timeCompilation(startNanos, durationNanos))

  override def registerInitial(changes: InitialChanges): Unit =
    profilers.foreach(_.registerInitial(changes))

  override def registerEvent(
      kind: String,
      inputs: Array[String],
      outputs: Array[String],
      reason: String
  ): Unit =
    profilers.foreach(_.registerEvent(kind, inputs, outputs, reason))

  override def registerCycle(
      invalidatedClasses: Array[String],
      invalidatedPackageObjects: Array[String],
      initialSources: Array[VirtualFileRef],
      invalidatedSources: Array[VirtualFileRef],
      recompiledClasses: Array[String],
      changesAfterRecompilation: Array[APIChange],
      nextInvalidations: Array[String],
      shouldCompileIncrementally: Boolean
  ): Unit =
    profilers.foreach(
      _.registerCycle(
        invalidatedClasses,
        invalidatedPackageObjects,
        initialSources,
        invalidatedSources,
        recompiledClasses,
        changesAfterRecompilation,
        nextInvalidations,
        shouldCompileIncrementally
      )
    )

  override def registerRun(): Unit =
    profilers.foreach(_.registerRun())
}
