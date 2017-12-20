/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util.appmacro

import scala.reflect.macros.blackbox

trait LinterDSL {
  def runLinter(ctx: blackbox.Context)(tree: ctx.Tree): Unit
}

object LinterDSL {
  object Empty extends LinterDSL {
    override def runLinter(ctx: blackbox.Context)(tree: ctx.Tree): Unit = ()
  }
}
