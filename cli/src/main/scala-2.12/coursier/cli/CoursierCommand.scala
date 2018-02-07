package coursier.cli

import caseapp.CommandParser
import caseapp.core.help.CommandsHelp

object CoursierCommand {

  val parser =
    CommandParser.nil
      .add(Bootstrap)
      .add(Fetch)
      .add(Launch)
      .add(Resolve)
      .add(SparkSubmit)
      .reverse

  val help =
    CommandsHelp.nil
      .add(Bootstrap)
      .add(Fetch)
      .add(Launch)
      .add(Resolve)
      .add(SparkSubmit)
      .reverse

}
