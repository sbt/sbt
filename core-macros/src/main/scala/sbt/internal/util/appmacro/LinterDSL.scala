package sbt.internal.util.appmacro

import scala.reflect.macros.blackbox

trait LinterDSL {
  def runLinter(ctx: blackbox.Context)(tree: ctx.Tree, isApplicableFromContext: => Boolean): Unit
}

object LinterDSL {
  object Empty extends LinterDSL {
    override def runLinter(ctx: blackbox.Context)(
        tree: ctx.Tree,
        isApplicableFromContext: => Boolean
    ): Unit = ()
  }
}
