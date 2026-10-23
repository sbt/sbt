package demo

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.plugins.{ PluginPhase, StandardPlugin }

class DemoPlugin extends StandardPlugin:
  val name = "demo"
  override val description = "does nothing"
  override def initialize(options: List[String])(using Context): List[PluginPhase] = Nil
