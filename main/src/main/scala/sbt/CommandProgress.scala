package sbt

/**
 * Tracks command execution progress. In addition to ExecuteProgress, this interface
 * adds command start and end events, and gives access to the sbt.State at the beginning
 * and end of each command.
 */
trait CommandProgress extends ExecuteProgress[Task] {

  /**
   * Called before a command starts processing. The command has not yet been parsed.
   *
   * @param cmd The command string
   * @param state The sbt.State before the command starts executing.
   */
  def beforeCommand(cmd: String, state: State): Unit

  /**
   * Called after a command finished execution.
   *
   * @param cmd    The command string.
   * @param result Left in case of an error. If the command cannot be parsed, it will be
   *               signalled as a ParseException with a detailed message. If the command
   *               was cancelled by the user, as sbt.Cancelled.
   */
  def afterCommand(cmd: String, result: Either[Throwable, State]): Unit
}
