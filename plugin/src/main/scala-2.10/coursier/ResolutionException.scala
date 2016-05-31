package coursier

final class ResolutionException(
  val message: String,
  val cause: Throwable = null
) extends Exception(
  message,
  cause,
  true,
  false // don't keep stack trace around (improves readability from the SBT console)
)
