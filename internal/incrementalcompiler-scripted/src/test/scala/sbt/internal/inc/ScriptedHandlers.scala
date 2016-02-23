package sbt.internal.inc

import sbt.internal.scripted._

class SleepingHandler(val handler: StatementHandler, delay: Long) extends StatementHandler {
  type State = handler.State
  override def initialState: State = handler.initialState
  override def apply(command: String, arguments: List[String], state: State): State = {
    val result = handler.apply(command, arguments, state)
    Thread.sleep(delay)
    result
  }
  override def finish(state: State) = handler.finish(state)
}

class IncScriptedHandlers extends HandlersProvider {
  def getHandlers(config: ScriptConfig): Map[Char, StatementHandler] = Map(
    '$' -> new SleepingHandler(new FileCommands(config.testDirectory()), 500),
    '#' -> CommentHandler,
    '>' -> new IncHandler(config.testDirectory(), config.logger())
  )
}