package sbt.internal.inc

import sbt.internal.scripted._

class IncScriptedHandlers extends HandlersProvider {
  def getHandlers(config: ScriptConfig): Map[Char, StatementHandler] = Map(
    '$' -> new FileCommands(config.testDirectory()),
    '#' -> CommentHandler,
    '>' -> new IncHandler(config.testDirectory(), config.logger())
  )
}