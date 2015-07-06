package coursier

import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.{ Executors, ExecutorService }

import scala.annotation.tailrec
import scalaz._
import scalaz.concurrent.{ Task, Strategy }

import java.io._

case class Files(
  cache: Seq[(String, File)],
  tmp: () => File,
  logger: Option[Files.Logger] = None,
  concurrentDownloadCount: Int = Files.defaultConcurrentDownloadCount
) {

  lazy val defaultPool =
    Executors.newFixedThreadPool(concurrentDownloadCount, Strategy.DefaultDaemonThreadFactory)

  def withLocal(artifact: Artifact): Artifact = {
    val isLocal =
      artifact.url.startsWith("file://") &&
        artifact.checksumUrls.values.forall(_.startsWith("file://"))

    def local(url: String) =
      if (url.startsWith("file://"))
        url.stripPrefix("file://")
      else
        cache.find{case (base, _) => url.startsWith(base)} match {
          case None => ???
          case Some((base, cacheDir)) =>
            cacheDir + "/" + url.stripPrefix(base)
        }

    if (artifact.extra.contains("local") || isLocal)
      artifact
    else
      artifact.copy(extra = artifact.extra + ("local" ->
        artifact.copy(
          url = local(artifact.url),
          checksumUrls = artifact.checksumUrls
            .mapValues(local)
            .toVector
            .toMap,
          extra = Map.empty
        )
      ))
  }

  def download(
    artifact: Artifact,
    withChecksums: Boolean = true
  )(implicit
    cachePolicy: CachePolicy,
    pool: ExecutorService = defaultPool
  ): Task[Seq[((File, String), FileError \/ Unit)]] = {
    val artifact0 = withLocal(artifact)
      .extra
      .getOrElse("local", artifact)

    val pairs =
      Seq(artifact0.url -> artifact.url) ++ {
        if (withChecksums)
          (artifact0.checksumUrls.keySet intersect artifact.checksumUrls.keySet)
            .toList
            .map(sumType => artifact0.checksumUrls(sumType) -> artifact.checksumUrls(sumType))
        else
          Nil
      }


    def locally(file: File) =
      Task {
        if (file.exists()) {
          logger.foreach(_.foundLocally(file))
          \/-(file)
        } else
          -\/(FileError.NotFound(file.toString): FileError)
      }

    // FIXME Things can go wrong here and are not properly handled,
    // e.g. what if the connection gets closed during the transfer?
    // (partial file on disk?)
    def remote(file: File, url: String) =
      Task {
        try {
          file.getParentFile.mkdirs()

          logger.foreach(_.downloadingArtifact(url))

          val url0 = new URL(url)
          val b = Array.fill[Byte](Files.bufferSize)(0)
          val in = new BufferedInputStream(url0.openStream(), Files.bufferSize)

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

          logger.foreach(_.downloadedArtifact(url, success = true))
          \/-(file)
        }
        catch { case e: Exception =>
          logger.foreach(_.downloadedArtifact(url, success = false))
          -\/(FileError.DownloadError(e.getMessage))
        }
      }


    val tasks =
      for ((f, url) <- pairs if url != ("file://" + f)) yield {
        val file = new File(f)
        cachePolicy(locally(file))(remote(file, url))
          .map(e => (file, url) -> e.map(_ => ()))
      }

    Nondeterminism[Task].gather(tasks)
  }

  def validateChecksum(
    artifact: Artifact,
    sumType: String
  )(implicit
    pool: ExecutorService = defaultPool
  ): Task[FileError \/ Unit] = {
    val artifact0 = withLocal(artifact)
      .extra
      .getOrElse("local", artifact)


    artifact0.checksumUrls.get(sumType) match {
      case Some(sumFile) =>
        Task {
          val sum = scala.io.Source.fromFile(sumFile)
            .getLines()
            .toStream
            .headOption
            .mkString
            .takeWhile(!_.isSpaceChar)

          val md = MessageDigest.getInstance(sumType)
          val is = new FileInputStream(new File(artifact0.url))
          try Files.withContent(is, md.update(_, 0, _))
          finally is.close()

          val digest = md.digest()
          val calculatedSum = f"${BigInt(1, digest)}%040x"

          if (sum == calculatedSum)
            \/-(())
          else
            -\/(FileError.WrongChecksum(sumType, calculatedSum, sum, artifact0.url, sumFile))
        }

      case None =>
        Task.now(-\/(FileError.ChecksumNotFound(sumType, artifact0.url)))
    }
  }

  def file(
    artifact: Artifact,
    checksum: Option[String] = Some("SHA-1")
  )(implicit
    cachePolicy: CachePolicy,
    pool: ExecutorService = defaultPool
  ): EitherT[Task, FileError, File] =
    EitherT{
      val res =
        download(artifact)
          .map(results =>
            results.head._2.map(_ => results.head._1._1)
          )

      checksum.fold(res) { sumType =>
        res
          .flatMap{
            case err @ -\/(_) => Task.now(err)
            case \/-(f) =>
              validateChecksum(artifact, sumType)
                .map(_.map(_ => f))
          }
      }
    }

}

object Files {
  
  val defaultConcurrentDownloadCount = 6

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

  def withContent(is: InputStream, f: (Array[Byte], Int) => Unit): Unit = {
    val data = Array.ofDim[Byte](16384)

    var nRead = is.read(data, 0, data.length)
    while (nRead != -1) {
      f(data, nRead)
      nRead = is.read(data, 0, data.length)
    }
  }

}

sealed trait FileError

object FileError {

  case class DownloadError(message: String) extends FileError
  case class NotFound(file: String) extends FileError
  case class Locked(file: String) extends FileError
  case class ChecksumNotFound(sumType: String, file: String) extends FileError
  case class WrongChecksum(sumType: String, got: String, expected: String, file: String, sumFile: String) extends FileError

}
