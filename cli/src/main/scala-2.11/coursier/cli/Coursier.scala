package coursier
package cli

import caseapp._
import caseapp.core.{ ArgsApp, CommandsMessages }

import shapeless.union.Union

// Temporary, see comment in Coursier below
case class CoursierCommandHelper(
  command: CoursierCommandHelper.U
) extends ArgsApp {
  def setRemainingArgs(remainingArgs: Seq[String], extraArgs: Seq[String]): Unit =
    command.unify.setRemainingArgs(remainingArgs, extraArgs)
  def remainingArgs: Seq[String] =
    command.unify.remainingArgs
  def apply(): Unit =
    command.unify.apply()
}

object CoursierCommandHelper {
  type U = Union.`'bootstrap -> Bootstrap, 'fetch -> Fetch, 'launch -> Launch, 'resolve -> Resolve, 'sparksubmit -> SparkSubmit`.T

  implicit val commandParser: CommandParser[CoursierCommandHelper] =
    CommandParser[U].map(CoursierCommandHelper(_))
  implicit val commandsMessages: CommandsMessages[CoursierCommandHelper] =
    CommandsMessages(CommandsMessages[U].messages)
}

object Coursier extends CommandAppOf[
  // Temporary using CoursierCommandHelper instead of the union type, until case-app
  // supports the latter directly.
  // Union.`'bootstrap -> Bootstrap, 'fetch -> Fetch, 'launch -> Launch, 'resolve -> Resolve`.T
  CoursierCommandHelper
] {
  override def appName = "Coursier"
  override def progName = "coursier"
  override def appVersion = coursier.util.Properties.version
}
