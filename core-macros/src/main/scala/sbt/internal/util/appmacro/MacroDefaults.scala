/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util.appmacro

import scala.reflect.macros.blackbox

object MacroDefaults {

  /**
   * Macro to generated default file tree repository. It must be defined as an untyped tree because
   * sbt.Keys is not available in this project. This is meant for internal use only, but must be
   * public because its a macro.
   * @param c the macro context
   * @return the tree expressing the default file tree repository.
   */
  def dynamicInputs(c: blackbox.Context): c.Tree = {
    import c.universe._
    q"sbt.internal.Continuous.dynamicInputs.value: @sbtUnchecked"
  }
}
