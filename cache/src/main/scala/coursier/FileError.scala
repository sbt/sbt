package coursier

import java.io.File

sealed abstract class FileError(
  val `type`: String,
  val message: String
) extends Product with Serializable

object FileError {

  final case class DownloadError(reason: String) extends FileError(
    "download error",
    reason
  )

  final case class NotFound(
    file: String,
    permanent: Option[Boolean] = None
  ) extends FileError(
    "not found",
    file
  )

  final case class Unauthorized(
    file: String,
    realm: Option[String]
  ) extends FileError(
    "unauthorized",
    file + realm.fold("")(" (" + _ + ")")
  )

  final case class ChecksumNotFound(
    sumType: String,
    file: String
  ) extends FileError(
    "checksum not found",
    file
  )

  final case class ChecksumFormatError(
    sumType: String,
    file: String
  ) extends FileError(
    "checksum format error",
    file
  )

  final case class WrongChecksum(
    sumType: String,
    got: String,
    expected: String,
    file: String,
    sumFile: String
  ) extends FileError(
    "wrong checksum",
    file
  )

  sealed abstract class Recoverable(
    `type`: String,
    message: String
  ) extends FileError(`type`, message)
  final case class Locked(file: File) extends Recoverable(
    "locked",
    file.toString
  )
  final case class ConcurrentDownload(url: String) extends Recoverable(
    "concurrent download",
    url
  )

}
