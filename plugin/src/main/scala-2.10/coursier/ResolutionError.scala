package coursier

import scala.collection.mutable.ArrayBuffer

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

    case err: ResolutionError.MetadataDownloadErrors =>
      err.description()

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

  case class MetadataDownloadErrors(errors: Seq[(Dependency, Seq[String])]) extends ResolutionError {
    def description(): String = {

      def grouped(errs: Seq[String]) =
        errs
          .map { s =>
            val idx = s.indexOf(": ")
            if (idx >= 0)
              (s.take(idx), s.drop(idx + ": ".length))
            else
              ("", s)
          }
          .groupBy(_._1)
          .mapValues(_.map(_._2))
          .toVector
          .sortBy(_._1)

      val lines = new ArrayBuffer[String]

      lines += s"Encountered ${errors.length} error(s) in dependency resolution:"

      for ((dep, errs) <- errors) {
        lines += s"  ${dep.module}:${dep.version}:"

        for ((type0, errs0) <- grouped(errs))
          if (type0.isEmpty)
            for (err <- errs0)
              lines += s"    $err"
          else
            errs0 match {
              case Seq(err) =>
                lines += s"    $type0: $err"
              case _ =>
                lines += s"    $type0:"
                for (err <- errs0)
                  lines += s"      $err"
            }
      }

      lines.mkString("\n")
    }
  }

  case class DownloadErrors(errors: Seq[FileError]) extends ResolutionError {

    def description(verbose: Boolean): String = {

      val groupedArtifactErrors = errors
        .groupBy(_.`type`)
        .mapValues(_.map(_.message).sorted)
        .toVector
        .sortBy(_._1)

      val b = new ArrayBuffer[String]

      for ((type0, errors) <- groupedArtifactErrors) {
        b += s"${errors.size} $type0"
        if (verbose)
          for (err <- errors)
            b += "  " + err
      }

      b.mkString("\n")
    }
  }
}
