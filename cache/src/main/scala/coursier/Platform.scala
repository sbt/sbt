package coursier

import java.io._
import java.net.URL

import scalaz._
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

  def readFully(is: => InputStream) =
    Task {
      \/.fromTryCatchNonFatal {
        val is0 = is
        val b =
          try readFullySync(is0)
          finally is0.close()

        new String(b, "UTF-8")
      } .leftMap{
        case e: java.io.FileNotFoundException if e.getMessage != null =>
          s"Not found: ${e.getMessage}"
        case e =>
          s"$e${Option(e.getMessage).fold("")(" (" + _ + ")")}"
      }
    }

  val artifact: Fetch.Content[Task] = { artifact =>
    EitherT {
      val url = Cache.url(artifact.url)

      val conn = url.openConnection()
      // Dummy user-agent instead of the default "Java/...",
      // so that we are not returned incomplete/erroneous metadata
      // (Maven 2 compatibility? - happens for snapshot versioning metadata,
      // this is SO FUCKING CRAZY)
      conn.setRequestProperty("User-Agent", "")
      readFully(conn.getInputStream())
    }
  }

  implicit def fetch(
    repositories: Seq[core.Repository]
  ): Fetch.Metadata[Task] =
    Fetch.from(repositories, Platform.artifact)

}
