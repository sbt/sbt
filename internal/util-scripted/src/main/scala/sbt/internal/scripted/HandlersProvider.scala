package sbt.internal.scripted

trait HandlersProvider {
  def getHandlers(config: ScriptConfig): Map[Char, StatementHandler]
}
