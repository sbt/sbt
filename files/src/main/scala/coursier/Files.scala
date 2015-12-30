package coursier

import java.net.URL
import java.nio.channels.{ OverlappingFileLockException, FileLock }
import java.security.MessageDigest
import java.util.concurrent.{ConcurrentHashMap, Executors, ExecutorService}

import scala.annotation.tailrec
import scalaz._
import scalaz.concurrent.{ Task, Strategy }

import java.io.{ Serializable => _, _ }

case class Files(
  cache: Seq[(String, File)],
  tmp: () => File,
  concurrentDownloadCount: Int = Files.defaultConcurrentDownloadCount
) {

  import Files.urlLocks

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
    checksums: Set[String],
    logger: Option[Files.Logger] = None
  )(implicit
    cachePolicy: CachePolicy,
    pool: ExecutorService = defaultPool
  ): Task[Seq[((File, String), FileError \/ Unit)]] = {
    val artifact0 = withLocal(artifact)
      .extra
      .getOrElse("local", artifact)

    val checksumPairs = checksums
      .intersect(artifact0.checksumUrls.keySet)
      .intersect(artifact.checksumUrls.keySet)
      .toSeq
      .map(sumType => artifact0.checksumUrls(sumType) -> artifact.checksumUrls(sumType))

    val pairs = (artifact0.url -> artifact.url) +: checksumPairs


    def locally(file: File, url: String): EitherT[Task, FileError, File] =
      EitherT {
        Task {
          if (file.exists()) {
            logger.foreach(_.foundLocally(url, file))
            \/-(file)
          } else
            -\/(FileError.NotFound(file.toString): FileError)
        }
      }

    def downloadIfDifferent(file: File, url: String): EitherT[Task, FileError, Boolean] = {
      ???
    }

    def test = {
      val t: Task[List[((File, String), FileError \/ Boolean)]] = Nondeterminism[Task].gather(checksumPairs.map { case (file, url) =>
        val f = new File(file)
        downloadIfDifferent(f, url).run.map((f, url) -> _)
      })

      t.map { l =>
        val noChange = l.nonEmpty && l.forall { case (_, e) => e.exists(x => x) }

        val anyChange = l.exists { case (_, e) => e.exists(x => !x) }
        val anyRecoverableError = l.exists {
          case (_, -\/(err: FileError.Recoverable)) => true
          case _ => false
        }
      }

    }

    // FIXME Things can go wrong here and are possibly not properly handled,
    // e.g. what if the connection gets closed during the transfer?
    // (partial file on disk?)
    def remote(file: File, url: String): EitherT[Task, FileError, File] =
      EitherT {
        Task {
          try {
            val o = new Object
            val prev = urlLocks.putIfAbsent(url, o)
            if (prev == null) {
              logger.foreach(_.downloadingArtifact(url, file))

              val r = try {
                val conn = new URL(url).openConnection() // FIXME Should this be closed?
                // Dummy user-agent instead of the default "Java/...",
                // so that we are not returned incomplete/erroneous metadata
                // (Maven 2 compatibility? - happens for snapshot versioning metadata,
                // this is SO FUCKING CRAZY)
                conn.setRequestProperty("User-Agent", "")

                for (len <- Option(conn.getContentLengthLong).filter(_ >= 0L))
                  logger.foreach(_.downloadLength(url, len))

                val in = new BufferedInputStream(conn.getInputStream, Files.bufferSize)

                val result = try {
                  file.getParentFile.mkdirs()
                  val out = new FileOutputStream(file)
                  try {
                    var lock: FileLock = null
                    try {
                      lock = out.getChannel.tryLock()
                      if (lock == null)
                        -\/(FileError.Locked(file))
                      else {
                        val b = Array.fill[Byte](Files.bufferSize)(0)

                        @tailrec
                        def helper(count: Long): Unit = {
                          val read = in.read(b)
                          if (read >= 0) {
                            out.write(b, 0, read)
                            out.flush()
                            logger.foreach(_.downloadProgress(url, count + read))
                            helper(count + read)
                          }
                        }

                        helper(0L)
                        \/-(file)
                      }
                    } catch { case e: OverlappingFileLockException =>
                      -\/(FileError.Locked(file))
                    } finally if (lock != null) lock.release()
                  } finally out.close()
                } finally in.close()

                for (lastModified <- Option(conn.getLastModified).filter(_ > 0L))
                  file.setLastModified(lastModified)

                result
              } catch { case e: Exception =>
                logger.foreach(_.downloadedArtifact(url, success = false))
                throw e
              } finally {
                urlLocks.remove(url)
              }
              logger.foreach(_.downloadedArtifact(url, success = true))
              r
            } else
              -\/(FileError.ConcurrentDownload(url))
          } catch { case e: Exception =>
            -\/(FileError.DownloadError(e.getMessage))
          }
        }
      }


    val tasks =
      for ((f, url) <- pairs) yield {
        val file = new File(f)

        if (url != ("file:" + f) && url != ("file://" + f)) {
          assert(!f.startsWith("file:/"), s"Wrong file detection: $f, $url")
          cachePolicy[FileError \/ File](
            _.isLeft )(
            locally(file, url).run )(
            _ => remote(file, url).run
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
  ): EitherT[Task, FileError, Unit] = {
    val artifact0 = withLocal(artifact)
      .extra
      .getOrElse("local", artifact)

    EitherT {
      artifact0.checksumUrls.get(sumType) match {
        case Some(sumFile) =>
          Task {
            val sum = scala.io.Source.fromFile(sumFile)
              .getLines()
              .toStream
              .headOption
              .mkString
              .takeWhile(!_.isSpaceChar)

            val f = new File(artifact0.url)
            val md = MessageDigest.getInstance(sumType)
            val is = new FileInputStream(f)
            val res = try {
              var lock: FileLock = null
              try {
                lock = is.getChannel.tryLock(0L, Long.MaxValue, true)
                if (lock == null)
                  -\/(FileError.Locked(f))
                else {
                  Files.withContent(is, md.update(_, 0, _))
                  \/-(())
                }
              }
              catch {
                case e: OverlappingFileLockException =>
                  -\/(FileError.Locked(f))
              }
              finally if (lock != null) lock.release()
            } finally is.close()

            res.flatMap { _ =>
              val digest = md.digest()
              val calculatedSum = f"${BigInt(1, digest)}%040x"

              if (sum == calculatedSum)
                \/-(())
              else
                -\/(FileError.WrongChecksum(sumType, calculatedSum, sum, artifact0.url, sumFile))
            }
          }

        case None =>
          Task.now(-\/(FileError.ChecksumNotFound(sumType, artifact0.url)))
      }
    }
  }

  def file(
    artifact: Artifact,
    checksums: Seq[Option[String]] = Seq(Some("SHA-1")),
    logger: Option[Files.Logger] = None
  )(implicit
    cachePolicy: CachePolicy,
    pool: ExecutorService = defaultPool
  ): EitherT[Task, FileError, File] = {
    val checksums0 = if (checksums.isEmpty) Seq(None) else checksums

    val res = EitherT {
      download(
        artifact,
        checksums = checksums0.collect { case Some(c) => c }.toSet,
        logger = logger
      ).map { results =>
        val checksum = checksums0.find {
          case None => true
          case Some(c) =>
            artifact.checksumUrls.get(c).exists { cUrl =>
              results.exists { case ((_, u), b) =>
                u == cUrl && b.isRight
              }
            }
        }

        val ((f, _), res) = results.head
        res.flatMap { _ =>
          checksum match {
            case None =>
              // FIXME All the checksums should be in the error, possibly with their URLs
              //       from artifact.checksumUrls
              -\/(FileError.ChecksumNotFound(checksums0.last.get, ""))
            case Some(c) => \/-((f, c))
          }
        }
      }
    }

    res.flatMap {
      case (f, None) => EitherT(Task.now[FileError \/ File](\/-(f)))
      case (f, Some(c)) =>
        validateChecksum(artifact, c).map(_ => f)
    }
  }

  def fetch(
    checksums: Seq[Option[String]] = Seq(Some("SHA-1")),
    logger: Option[Files.Logger] = None
  )(implicit
    cachePolicy: CachePolicy,
    pool: ExecutorService = defaultPool
  ): Fetch.Content[Task] = {
    artifact =>
      file(artifact, checksums = checksums, logger = logger)(cachePolicy).leftMap(_.message).map { f =>
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

  private val urlLocks = new ConcurrentHashMap[String, Object]

  trait Logger {
    def foundLocally(url: String, f: File): Unit = {}
    def downloadingArtifact(url: String, file: File): Unit = {}
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

sealed trait FileError extends Product with Serializable {
  def message: String
}

object FileError {

  case class DownloadError(message0: String) extends FileError {
    def message = s"Download error: $message0"
  }
  case class NotFound(file: String) extends FileError {
    def message = s"$file: not found"
  }
  case class ChecksumNotFound(sumType: String, file: String) extends FileError {
    def message = s"$file: $sumType checksum not found"
  }
  case class WrongChecksum(sumType: String, got: String, expected: String, file: String, sumFile: String) extends FileError {
    def message = s"$file: $sumType checksum validation failed"
  }

  sealed trait Recoverable extends FileError
  case class Locked(file: File) extends Recoverable {
    def message = s"$file: locked"
  }
  case class ConcurrentDownload(url: String) extends Recoverable {
    def message = s"$url: concurrent download"
  }

}
