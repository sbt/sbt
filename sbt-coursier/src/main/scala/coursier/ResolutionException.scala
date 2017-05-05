package coursier

final class ResolutionException(
  val error: ResolutionError
) extends Exception(
  error.message,
  error.cause.orNull
) {
  // not using the 4-arg Exception constructor, only available with Java >= 7

  setStackTrace(Array()) // don't keep stack trace around (improves readability from the SBT console)
}
