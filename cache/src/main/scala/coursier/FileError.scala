package coursier

import java.io.File

sealed abstract class FileError(val message: String) extends Product with Serializable

object FileError {

  final case class DownloadError(reason: String) extends FileError(s"Download error: $reason")

  final case class NotFound(
    file: String,
    permanent: Option[Boolean] = None
  ) extends FileError(s"Not found: $file")

  final case class ChecksumNotFound(
    sumType: String,
    file: String
  ) extends FileError(s"$sumType checksum not found: $file")

  final case class ChecksumFormatError(
    sumType: String,
    file: String
  ) extends FileError(s"Unrecognized $sumType checksum format in $file")

  final case class WrongChecksum(
    sumType: String,
    got: String,
    expected: String,
    file: String,
    sumFile: String
  ) extends FileError(s"$sumType checksum validation failed: $file")

  sealed abstract class Recoverable(message: String) extends FileError(message)
  final case class Locked(file: File) extends Recoverable(s"Locked: $file")
  final case class ConcurrentDownload(url: String) extends Recoverable(s"Concurrent download: $url")

}
