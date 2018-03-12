package coursier

import java.io._
import java.nio.charset.Charset

import coursier.interop.scalaz._
import coursier.util.EitherT

import scala.util.{Failure, Success, Try}
import scalaz.concurrent.Task

object Platform {

  def readFullySync(is: InputStream) = {
    val buffer = new ByteArrayOutputStream()
    val data = Array.ofDim[Byte](16384)

    var nRead = is.read(data, 0, data.length)
    while (nRead != -1) {
      buffer.write(data, 0, nRead)
      nRead = is.read(data, 0, data.length)
    }

    buffer.flush()
    buffer.toByteArray
  }

  private lazy val UTF_8 = Charset.forName("UTF-8")

  def readFully(is: => InputStream): Task[Either[String, String]] =
    Task {
      val t = Try {
        val is0 = is
        val b =
          try readFullySync(is0)
          finally is0.close()

        new String(b, UTF_8)
      }

      t match {
        case Success(r) => Right(r)
        case Failure(e: java.io.FileNotFoundException) if e.getMessage != null =>
          Left(s"Not found: ${e.getMessage}")
        case Failure(e) =>
          Left(s"$e${Option(e.getMessage).fold("")(" (" + _ + ")")}")
      }
    }

  val artifact: Fetch.Content[Task] = { artifact =>
    EitherT {
      val conn = Cache.urlConnection(artifact.url, artifact.authentication)
      readFully(conn.getInputStream)
    }
  }

  def fetch(
    repositories: Seq[core.Repository]
  ): Fetch.Metadata[Task] =
    Fetch.from(repositories, Platform.artifact)

}
