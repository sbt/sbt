package coursier
package cli

import caseapp._
import caseapp.core.{ ArgsApp, CommandMessages, CommandsMessages }
import caseapp.core.util.pascalCaseSplit
import caseapp.util.AnnotationOption

import shapeless._
import shapeless.labelled.FieldType
import shapeless.union.Union

// Temporary, see comment in Coursier below
final case class CoursierCommandHelper(
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

  // Partially deriving these ones manually, to circumvent more-or-less random failures during auto derivation
  // Only running into those with the new custom sbt launcher though :-|

  implicit def commandParser: CommandParser[CoursierCommandHelper] =
    CommandParser.ccons(
      Witness('bootstrap),
      AnnotationOption[CommandName, Bootstrap],
      Parser[Bootstrap],
      CommandParser.ccons(
        Witness('fetch),
        AnnotationOption[CommandName, Fetch],
        Parser[Fetch],
        CommandParser.ccons(
          Witness('launch),
          AnnotationOption[CommandName, Launch],
          Parser[Launch],
          CommandParser.ccons(
            Witness('resolve),
            AnnotationOption[CommandName, Resolve],
            Parser[Resolve],
            CommandParser.ccons(
              Witness('sparksubmit),
              AnnotationOption[CommandName, SparkSubmit],
              Parser[SparkSubmit],
              CommandParser.cnil
            )
          )
        )
      )
    ).map(CoursierCommandHelper(_))


  // Cut-n-pasted from caseapp.core.CommandsMessages.ccons, fixing the type of argsName
  private def commandsMessagesCCons[K <: Symbol, H, T <: Coproduct]
   (implicit
     key: Witness.Aux[K],
     commandName: AnnotationOption[CommandName, H],
     parser: Strict[Parser[H]],
     argsName: AnnotationOption[ArgsName, H],
     tail: CommandsMessages[T]
   ): CommandsMessages[FieldType[K, H] :+: T] = {
    // FIXME Duplicated in CommandParser.ccons
    val name = commandName().map(_.commandName).getOrElse {
      pascalCaseSplit(key.value.name.toList.takeWhile(_ != '$'))
        .map(_.toLowerCase)
        .mkString("-")
    }

    CommandsMessages((name -> CommandMessages(
      parser.value.args,
      argsName().map(_.argsName)
    )) +: tail.messages)
  }


  implicit def commandsMessages: CommandsMessages[CoursierCommandHelper] =
    CommandsMessages(
      commandsMessagesCCons(
        Witness('bootstrap),
        AnnotationOption[CommandName, Bootstrap],
        Parser[Bootstrap],
        AnnotationOption[ArgsName, Bootstrap],
        commandsMessagesCCons(
          Witness('fetch),
          AnnotationOption[CommandName, Fetch],
          Parser[Fetch],
          AnnotationOption[ArgsName, Fetch],
          commandsMessagesCCons(
            Witness('launch),
            AnnotationOption[CommandName, Launch],
            Parser[Launch],
            AnnotationOption[ArgsName, Launch],
            commandsMessagesCCons(
              Witness('resolve),
              AnnotationOption[CommandName, Resolve],
              Parser[Resolve],
              AnnotationOption[ArgsName, Resolve],
              commandsMessagesCCons(
                Witness('sparksubmit),
                AnnotationOption[CommandName, SparkSubmit],
                Parser[SparkSubmit],
                AnnotationOption[ArgsName, SparkSubmit],
                CommandsMessages.cnil
              )
            )
          )
        )
      ).messages
    )
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
