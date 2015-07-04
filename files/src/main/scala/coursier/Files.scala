package coursier

import java.net.{ URI, URL }

import coursier.core.Repository.CachePolicy

import scala.annotation.tailrec
import scalaz.{ -\/, \/-, \/, EitherT }
import scalaz.concurrent.Task

import java.io._

case class Files(
  cache: Seq[(String, File)],
  tmp: () => File,
  logger: Option[Files.Logger] = None
) {

  def file(
    artifact: Artifact,
    cachePolicy: CachePolicy
  ): EitherT[Task, String, File] = {

    if (artifact.url.startsWith("file:///")) {
      val f = new File(new URI(artifact.url) .getPath)
      EitherT(Task.now(
        if (f.exists()) {
          logger.foreach(_.foundLocally(f))
          \/-(f)
        } else -\/("Not found")
      ))
    } else {
      cache.find{case (base, _) => artifact.url.startsWith(base)} match {
        case None => ???
        case Some((base, cacheDir)) =>
          val file = new File(cacheDir, artifact.url.stripPrefix(base))

          def locally = {
            Task {
              if (file.exists()) {
                logger.foreach(_.foundLocally(file))
                \/-(file)
              }
              else -\/("Not found in cache")
            }
          }

          def remote = {
            // FIXME A lot of things can go wrong here and are not properly handled:
            //  - checksums should be validated
            //  - what if the connection gets closed during the transfer (partial file on disk)?
            //  - what if someone is trying to write this file at the same time? (no locking of any kind yet)
            //  - ...

            Task {
              try {
                file.getParentFile.mkdirs()

                logger.foreach(_.downloadingArtifact(artifact.url))

                val url = new URL(artifact.url)
                val b = Array.fill[Byte](Files.bufferSize)(0)
                val in = new BufferedInputStream(url.openStream(), Files.bufferSize)

                try {
                  val out = new FileOutputStream(file)
                  try {
                    @tailrec
                    def helper(): Unit = {
                      val read = in.read(b)
                      if (read >= 0) {
                        out.write(b, 0, read)
                        helper()
                      }
                    }

                    helper()
                  } finally out.close()
                } finally in.close()

                logger.foreach(_.downloadedArtifact(artifact.url, success = true))
                \/-(file)
              }
              catch { case e: Exception =>
                logger.foreach(_.downloadedArtifact(artifact.url, success = false))
                -\/(e.getMessage)
              }
            }
          }

          EitherT(cachePolicy(locally)(remote))
      }
    }
  }

}

object Files {

  // FIXME This kind of side-effecting API is lame, we should aim at a more functional one.
  trait Logger {
    def foundLocally(f: File): Unit
    def downloadingArtifact(url: String): Unit
    def downloadedArtifact(url: String, success: Boolean): Unit
  }

  var bufferSize = 1024*1024

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
      } .leftMap(_.getMessage)
    }

}
