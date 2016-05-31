package coursier

final class ResolutionException(
  val error: ResolutionError
) extends Exception(
  error.message,
  error.cause.orNull,
  true,
  false // don't keep stack trace around (improves readability from the SBT console)
)
