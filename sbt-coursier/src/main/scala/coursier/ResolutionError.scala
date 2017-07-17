package coursier

import scala.collection.mutable.ArrayBuffer

sealed abstract class ResolutionError extends Product with Serializable {

  final def cause: Option[Throwable] = this match {
    case ResolutionError.MaximumIterationsReached => None
    case ResolutionError.UnknownException(ex) => Some(ex)
    case ResolutionError.UnknownDownloadException(ex) => Some(ex)
    case _: ResolutionError.Conflicts => None
    case _: ResolutionError.MetadataDownloadErrors => None
    case _: ResolutionError.DownloadErrors => None
  }

  final def message: String = this match {
    case ResolutionError.MaximumIterationsReached =>
      "Maximum number of iteration of dependency resolution reached"
    case ResolutionError.UnknownException(_) =>
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

  final def exception(): ResolutionException =
    new ResolutionException(this)

  final def throwException(): Nothing =
    throw exception()
}

object ResolutionError {

  case object MaximumIterationsReached extends ResolutionError
  final case class UnknownException(ex: Throwable) extends ResolutionError
  final case class UnknownDownloadException(ex: Throwable) extends ResolutionError
  final case class Conflicts(description: String) extends ResolutionError

  final case class MetadataDownloadErrors(errors: Seq[((Module, String), Seq[String])]) extends ResolutionError {
    def description(): String = {

      def grouped(errs: Seq[String]) =
        errs
          .map { s =>
            s.split(": ", 2) match {
              case Array(k, v) => (k, v)
              case _ => ("", s)
            }
          }
          .groupBy(_._1)
          .mapValues(_.map(_._2))
          .toVector
          .sortBy(_._1)


      val lines = new ArrayBuffer[String]
      def print(s: String, indentLevel: Int = 0) =
        lines += "  " * indentLevel * 2 + s
      def result = lines.mkString("\n")


      print(s"Encountered ${errors.length} error(s) in dependency resolution:")

      for (((mod, ver), errs) <- errors) {
        print(s"$mod:$ver:", 1)

        for ((type0, errs0) <- grouped(errs))
          if (type0.isEmpty)
            for (err <- errs0)
              print(err, 2)
          else
            errs0 match {
              case Seq(err) =>
                print(s"$type0: $err", 2)
              case _ =>
                print(s"$type0:", 2)
                for (err <- errs0)
                  print(err, 3)
            }
      }

      result
    }
  }

  final case class DownloadErrors(errors: Seq[FileError]) extends ResolutionError {

    def description(verbose: Boolean): String = {

      val groupedArtifactErrors = errors
        .groupBy(_.`type`)
        .mapValues(_.map(_.message).sorted)
        .toVector
        .sortBy(_._1)


      val lines = new ArrayBuffer[String]
      def print(s: String, indentLevel: Int = 0) =
        lines += "  " * indentLevel * 2 + s
      def result = lines.mkString("\n")


      for ((type0, errors) <- groupedArtifactErrors) {
        print(s"${errors.size} $type0")
        if (verbose)
          for (err <- errors)
            print(err, 1)
      }

      result
    }
  }
}
