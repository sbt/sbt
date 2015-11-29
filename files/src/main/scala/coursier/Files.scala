package coursier

import java.net.URL
import java.nio.channels.{ OverlappingFileLockException, FileLock }
import java.security.MessageDigest
import java.util.concurrent.{ Executors, ExecutorService }

import scala.annotation.tailrec
import scalaz._
import scalaz.concurrent.{ Task, Strategy }

import java.io._

case class Files(
  cache: Seq[(String, File)],
  tmp: () => File,
  concurrentDownloadCount: Int = Files.defaultConcurrentDownloadCount
) {

  lazy val defaultPool =
    Executors.newFixedThreadPool(concurrentDownloadCount, Strategy.DefaultDaemonThreadFactory)

  def withLocal(artifact: Artifact): Artifact = {
    def local(url: String) =
      if (url.startsWith("file:///"))
        url.stripPrefix("file://")
      else if (url.startsWith("file:/"))
        url.stripPrefix("file:")
      else
        cache.find { case (base, _) => url.startsWith(base) } match {
          case None =>
            // FIXME Means we were handed an artifact from repositories other than the known ones
            println(cache.mkString("\n"))
            println(url)
            ???
          case Some((base, cacheDir)) =>
            cacheDir + "/" + url.stripPrefix(base)
        }

    if (artifact.extra.contains("local"))
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
    withChecksums: Boolean = true,
    logger: Option[Files.Logger] = None
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


    def locally(file: File, url: String) =
      Task {
        if (file.exists()) {
          logger.foreach(_.foundLocally(url, file))
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

          val conn = new URL(url).openConnection() // FIXME Should this be closed?
          // Dummy user-agent instead of the default "Java/...",
          // so that we are not returned incomplete/erroneous metadata
          // (Maven 2 compatibility? - happens for snapshot versioning metadata,
          // this is SO FUCKING CRAZY)
          conn.setRequestProperty("User-Agent", "")

          for (len <- Option(conn.getContentLengthLong).filter(_ >= 0L))
            logger.foreach(_.downloadLength(url, len))

          val in = new BufferedInputStream(conn.getInputStream, Files.bufferSize)

          val result =
            try {
              val out = new FileOutputStream(file)
              try {
                var lock: FileLock = null
                try {
                  lock = out.getChannel.tryLock()
                  if (lock == null)
                    -\/(FileError.Locked(file.toString))
                  else {
                    val b = Array.fill[Byte](Files.bufferSize)(0)

                    @tailrec
                    def helper(count: Long): Unit = {
                      val read = in.read(b)
                      if (read >= 0) {
                        out.write(b, 0, read)
                        logger.foreach(_.downloadProgress(url, count + read))
                        helper(count + read)
                      }
                    }

                    helper(0L)
                    \/-(file)
                  }
                }
                catch {
                  case e: OverlappingFileLockException =>
                    -\/(FileError.Locked(file.toString))
                }
                finally if (lock != null) lock.release()
              } finally out.close()
            } finally in.close()

          for (lastModified <- Option(conn.getLastModified).filter(_ > 0L))
            file.setLastModified(lastModified)

          logger.foreach(_.downloadedArtifact(url, success = true))
          result
        }
        catch { case e: Exception =>
          logger.foreach(_.downloadedArtifact(url, success = false))
          -\/(FileError.DownloadError(e.getMessage))
        }
      }


    val tasks =
      for ((f, url) <- pairs) yield {
        val file = new File(f)

        if (url != ("file:" + f) && url != ("file://" + f)) {
          assert(!f.startsWith("file:/"), s"Wrong file detection: $f, $url")
          cachePolicy[FileError \/ File](
            _.isLeft )(
            locally(file, url) )(
            _ => remote(file, url)
          ).map(e => (file, url) -> e.map(_ => ()))
        } else
          Task {
            (file, url) -> {
              if (file.exists())
                \/-(())
              else
                -\/(FileError.NotFound(file.toString))
            }
          }
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
    checksum: Option[String] = Some("SHA-1"),
    logger: Option[Files.Logger] = None
  )(implicit
    cachePolicy: CachePolicy,
    pool: ExecutorService = defaultPool
  ): EitherT[Task, FileError, File] =
    EitherT {
      val res = download(artifact, withChecksums = checksum.nonEmpty, logger = logger).map {
        results =>
          val ((f, _), res) = results.head
          res.map(_ => f)
      }

      checksum.fold(res) { sumType =>
        res.flatMap {
          case err @ -\/(_) => Task.now(err)
          case \/-(f) =>
            validateChecksum(artifact, sumType)
              .map(_.map(_ => f))
        }
      }
    }

  def fetch(
    checksum: Option[String] = Some("SHA-1"),
    logger: Option[Files.Logger] = None
  )(implicit
    cachePolicy: CachePolicy,
    pool: ExecutorService = defaultPool
  ): Repository.Fetch[Task] = {
    artifact =>
      file(artifact, checksum = checksum, logger = logger)(cachePolicy).leftMap(_.message).map { f =>
        // FIXME Catch error here?
        scala.io.Source.fromFile(f)("UTF-8").mkString
      }
  }

}

object Files {

  lazy val ivy2Local = MavenRepository(
    new File(sys.props("user.home") + "/.ivy2/local/").toURI.toString,
    ivyLike = true
  )

  val defaultConcurrentDownloadCount = 6

  trait Logger {
    def foundLocally(url: String, f: File): Unit = {}
    def downloadingArtifact(url: String): Unit = {}
    def downloadLength(url: String, length: Long): Unit = {}
    def downloadProgress(url: String, downloaded: Long): Unit = {}
    def downloadedArtifact(url: String, success: Boolean): Unit = {}
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

sealed trait FileError {
  def message: String
}

object FileError {

  case class DownloadError(message0: String) extends FileError {
    def message = s"Download error: $message0"
  }
  case class NotFound(file: String) extends FileError {
    def message = s"$file: not found"
  }
  case class Locked(file: String) extends FileError {
    def message = s"$file: locked"
  }
  case class ChecksumNotFound(sumType: String, file: String) extends FileError {
    def message = s"$file: $sumType checksum not found"
  }
  case class WrongChecksum(sumType: String, got: String, expected: String, file: String, sumFile: String) extends FileError {
    def message = s"$file: $sumType checksum validation failed"
  }

}
