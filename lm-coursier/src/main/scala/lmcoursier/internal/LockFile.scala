package lmcoursier.internal

import java.io.File
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import sjsonnew.support.scalajson.unsafe.{ Converter, Parser, PrettyPrinter }
import scala.util.{ Try, Success, Failure }

object LockFile {
  import LockFileFormats.given

  val defaultLockFileName = "deps.lock"

  def read(lockFile: File): Either[String, LockFileData] = {
    if (!lockFile.exists()) {
      Left(s"Lock file does not exist: ${lockFile.getAbsolutePath}")
    } else {
      Try {
        val content = new String(Files.readAllBytes(lockFile.toPath), StandardCharsets.UTF_8)
        val json = Parser.parseFromString(content).get
        Converter.fromJson[LockFileData](json).get
      } match {
        case Success(data) => Right(data)
        case Failure(ex)   => Left(s"Failed to parse lock file: ${ex.getMessage}")
      }
    }
  }

  def write(lockFile: File, data: LockFileData): Either[String, Unit] = {
    Try {
      val json = Converter.toJson(data).get
      val content = PrettyPrinter(json)
      lockFile.getParentFile.mkdirs()
      Files.writeString(lockFile.toPath, content)
    } match {
      case Success(_)  => Right(())
      case Failure(ex) => Left(s"Failed to write lock file: ${ex.getMessage}")
    }
  }

  def getLockFile(baseDirectory: File): File =
    new File(baseDirectory, defaultLockFileName)

  def exists(baseDirectory: File): Boolean =
    getLockFile(baseDirectory).exists()
}
