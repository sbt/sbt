/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.nio.file.{ WatchService => _ }

import sbt.internal.util.appmacro.MacroDefaults
import sbt.nio.file.Glob

import scala.collection.mutable
import scala.language.experimental.macros

object FileTree {
  private[sbt] trait DynamicInputs {
    def value: Option[mutable.Set[Glob]]
  }
  private[sbt] object DynamicInputs {
    def empty: DynamicInputs = new impl(Some(mutable.Set.empty[Glob]))
    final val none: DynamicInputs = new impl(None)
    private final class impl(override val value: Option[mutable.Set[Glob]]) extends DynamicInputs
    implicit def default: DynamicInputs = macro MacroDefaults.dynamicInputs
  }
}
