package coursier

sealed abstract class ResolutionError extends Product with Serializable {
  def cause: Option[Throwable] = this match {
    case ResolutionError.MaximumIterationsReached => None
    case ResolutionError.UnknownException(ex) => Some(ex)
    case ResolutionError.UnknownDownloadException(ex) => Some(ex)
    case _: ResolutionError.Conflicts => None
    case _: ResolutionError.MetadataDownloadErrors => None
    case _: ResolutionError.DownloadErrors => None
  }

  def message: String = this match {
    case ResolutionError.MaximumIterationsReached =>
      "Maximum number of iteration of dependency resolution reached"
    case ResolutionError.UnknownException(ex) =>
      "Exception during resolution"
    case ResolutionError.UnknownDownloadException(ex) =>
      "Error while downloading / verifying artifacts"
    case ResolutionError.Conflicts(description) =>
      description

    case ResolutionError.MetadataDownloadErrors(errors) =>
      s"Encountered ${errors.length} error(s) in dependency resolution:\n" +
        errors.map {
          case (dep, errs) =>
            s"  ${dep.module}:${dep.version}:\n" +
              errs
                .map("    " + _.replace("\n", "    \n"))
                .mkString("\n")
        }.mkString("\n")

    case err: ResolutionError.DownloadErrors =>
      err.description(verbose = true)
  }

  def exception(): ResolutionException =
    new ResolutionException(this)

  def throwException(): Nothing =
    throw exception()
}

object ResolutionError {

  case object MaximumIterationsReached extends ResolutionError
  case class UnknownException(ex: Throwable) extends ResolutionError
  case class UnknownDownloadException(ex: Throwable) extends ResolutionError
  case class Conflicts(description: String) extends ResolutionError
  case class MetadataDownloadErrors(errors: Seq[(Dependency, Seq[String])]) extends ResolutionError

  case class DownloadErrors(errors: Seq[FileError]) extends ResolutionError {

    def description(verbose: Boolean): String = {

      val groupedArtifactErrors = errors
        .groupBy(_.`type`)
        .mapValues(_.map(_.message).sorted)
        .toVector
        .sortBy(_._1)

      val b = new StringBuilder

      for ((type0, errors) <- groupedArtifactErrors) {
        b ++= s"${errors.size} $type0"
        if (verbose)
          for (err <- errors)
            b ++= "  " + err
      }

      b.result()
    }
  }
}
