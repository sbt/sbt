package coursier.sbtcoursier

import sbt.Logger

import scala.util.{Failure, Success, Try}

object Settings {

  private val baseDefaultVerbosityLevel =
    if (System.console() == null) // non interactive mode
      0
    else
      1

  def defaultVerbosityLevel(logger: Logger): Int = {

    def fromOption(value: Option[String], description: String): Option[Int] =
      value
        .filter(_.nonEmpty)
        .flatMap { str =>
          Try(str.toInt) match {
            case Success(level) => Some(level)
            case Failure(_) =>
              logger.warn(
                s"unrecognized $description value (should be an integer), ignoring it."
              )
              None
          }
        }

    val fromEnv = fromOption(
      sys.env.get("COURSIER_VERBOSITY"),
      "COURSIER_VERBOSITY environment variable"
    )

    def fromProps = fromOption(
      sys.props.get("coursier.verbosity"),
      "Java property coursier.verbosity"
    )

    fromEnv
      .orElse(fromProps)
      .getOrElse(baseDefaultVerbosityLevel)
  }

}
