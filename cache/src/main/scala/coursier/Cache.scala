package coursier

import java.net.{HttpURLConnection, URL}
import java.nio.channels.{ OverlappingFileLockException, FileLock }
import java.security.MessageDigest
import java.util.concurrent.{ConcurrentHashMap, Executors, ExecutorService}

import scala.annotation.tailrec
import scalaz._
import scalaz.concurrent.{ Task, Strategy }

import java.io.{ Serializable => _, _ }

object Cache {

  private def withLocal(artifact: Artifact, cache: Seq[(String, File)]): Artifact = {
    def local(url: String) =
      if (url.startsWith("file:///"))
        url.stripPrefix("file://")
      else if (url.startsWith("file:/"))
        url.stripPrefix("file:")
      else {
        val localPathOpt = cache.collectFirst {
          case (base, cacheDir) if url.startsWith(base) =>
            cacheDir + "/" + url.stripPrefix(base)
        }

        localPathOpt.getOrElse {
          // FIXME Means we were handed an artifact from repositories other than the known ones
          println(cache.mkString("\n"))
          println(url)
          ???
        }
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

  private def download(
    artifact: Artifact,
    cache: Seq[(String, File)],
    checksums: Set[String],
    cachePolicy: CachePolicy,
    pool: ExecutorService,
    logger: Option[Logger] = None
  ): Task[Seq[((File, String), FileError \/ Unit)]] = {

    implicit val pool0 = pool

    val artifact0 = withLocal(artifact, cache)
      .extra
      .getOrElse("local", artifact)

    val pairs =
      Seq(artifact0.url -> artifact.url) ++ {
        checksums
          .intersect(artifact0.checksumUrls.keySet)
          .intersect(artifact.checksumUrls.keySet)
          .toSeq
          .map(sumType => artifact0.checksumUrls(sumType) -> artifact.checksumUrls(sumType))
      }

    def urlConn(url: String) = {
      val conn = new URL(url).openConnection() // FIXME Should this be closed?
      // Dummy user-agent instead of the default "Java/...",
      // so that we are not returned incomplete/erroneous metadata
      // (Maven 2 compatibility? - happens for snapshot versioning metadata,
      // this is SO FSCKING CRAZY)
      conn.setRequestProperty("User-Agent", "")
      conn
    }


    def fileLastModified(file: File): EitherT[Task, FileError, Option[Long]] =
      EitherT {
        Task {
          \/- {
            val lastModified = file.lastModified()
            if (lastModified > 0L)
              Some(lastModified)
            else
              None
          } : FileError \/ Option[Long]
        }
      }

    def urlLastModified(url: String): EitherT[Task, FileError, Option[Long]] =
      EitherT {
        Task {
          urlConn(url) match {
            case c: HttpURLConnection =>
              c.setRequestMethod("HEAD")
              val remoteLastModified = c.getLastModified

              \/- {
                if (remoteLastModified > 0L)
                  Some(remoteLastModified)
                else
                  None
              }

            case other =>
              -\/(FileError.DownloadError(s"Cannot do HEAD request with connection $other ($url)"))
          }
        }
      }

    def shouldDownload(file: File, url: String): EitherT[Task, FileError, Boolean] =
      for {
        fileLastModOpt <- fileLastModified(file)
        urlLastModOpt <- urlLastModified(url)
      } yield {
        val fromDatesOpt = for {
          fileLastMod <- fileLastModOpt
          urlLastMod <- urlLastModOpt
        } yield fileLastMod < urlLastMod

        fromDatesOpt.getOrElse(true)
      }

    // FIXME Things can go wrong here and are not properly handled,
    // e.g. what if the connection gets closed during the transfer?
    // (partial file on disk?)
    def remote(file: File, url: String): EitherT[Task, FileError, Unit] =
      EitherT {
        Task {
          try {
            val o = new Object
            val prev = urlLocks.putIfAbsent(url, o)
            if (prev == null) {
              logger.foreach(_.downloadingArtifact(url, file))

              val r = try {
                val conn = urlConn(url)

                for (len <- Option(conn.getContentLengthLong).filter(_ >= 0L))
                  logger.foreach(_.downloadLength(url, len))

                val in = new BufferedInputStream(conn.getInputStream, bufferSize)

                val result =
                  try {
                    file.getParentFile.mkdirs()
                    val out = new FileOutputStream(file)
                    try {
                      var lock: FileLock = null
                      try {
                        lock = out.getChannel.tryLock()
                        if (lock == null)
                          -\/(FileError.Locked(file))
                        else {
                          val b = Array.fill[Byte](bufferSize)(0)

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
                          \/-(())
                        }
                      }
                      catch {
                        case e: OverlappingFileLockException =>
                          -\/(FileError.Locked(file))
                      }
                      finally if (lock != null) lock.release()
                    } finally out.close()
                  } finally in.close()

                for (lastModified <- Option(conn.getLastModified).filter(_ > 0L))
                  file.setLastModified(lastModified)

                result
              }
              catch { case e: Exception =>
                logger.foreach(_.downloadedArtifact(url, success = false))
                throw e
              }
              finally {
                urlLocks.remove(url)
              }
              logger.foreach(_.downloadedArtifact(url, success = true))
              r
            } else
              -\/(FileError.ConcurrentDownload(url))
          }
          catch { case e: Exception =>
            -\/(FileError.DownloadError(e.getMessage))
          }
        }
      }

    def checkFileExists(file: File, url: String): EitherT[Task, FileError, Unit] =
      EitherT {
        Task {
          if (file.exists()) {
            logger.foreach(_.foundLocally(url, file))
            \/-(())
          } else
            -\/(FileError.NotFound(file.toString))
        }
      }

    val tasks =
      for ((f, url) <- pairs) yield {
        val file = new File(f)

        val res =
          if (url.startsWith("file:/")) {
            def filtered(s: String) =
              s.stripPrefix("file:/").stripPrefix("//").stripSuffix("/")
            assert(
              filtered(url) == filtered(file.toURI.toString),
              s"URL: ${filtered(url)}, file: ${filtered(file.toURI.toString)}"
            )
            checkFileExists(file, url)
          } else
            cachePolicy match {
              case CachePolicy.LocalOnly =>
                checkFileExists(file, url)
              case CachePolicy.UpdateChanging | CachePolicy.Update =>
                shouldDownload(file, url).flatMap {
                  case true =>
                    remote(file, url)
                  case false =>
                    EitherT(Task.now(\/-(()) : FileError \/ Unit))
                }
              case CachePolicy.FetchMissing =>
                checkFileExists(file, url) orElse remote(file, url)
              case CachePolicy.ForceDownload =>
                remote(file, url)
            }

        res.run.map((file, url) -> _)
      }

    Nondeterminism[Task].gather(tasks)
  }

  def validateChecksum(
    artifact: Artifact,
    sumType: String,
    cache: Seq[(String, File)],
    pool: ExecutorService
  ): EitherT[Task, FileError, Unit] = {

    implicit val pool0 = pool

    val artifact0 = withLocal(artifact, cache)
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
                  withContent(is, md.update(_, 0, _))
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
    cache: Seq[(String, File)],
    cachePolicy: CachePolicy,
    checksums: Seq[Option[String]] = Seq(Some("SHA-1")),
    logger: Option[Logger] = None,
    pool: ExecutorService = defaultPool
  ): EitherT[Task, FileError, File] = {

    implicit val pool0 = pool

    val checksums0 = if (checksums.isEmpty) Seq(None) else checksums

    val res = EitherT {
      download(
        artifact,
        cache,
        checksums = checksums0.collect { case Some(c) => c }.toSet,
        cachePolicy,
        pool,
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
        validateChecksum(artifact, c, cache, pool).map(_ => f)
    }
  }

  def fetch(
    cache: Seq[(String, File)],
    cachePolicy: CachePolicy,
    checksums: Seq[Option[String]] = Seq(Some("SHA-1")),
    logger: Option[Logger] = None,
    pool: ExecutorService = defaultPool
  ): Fetch.Content[Task] = {
    artifact =>
      file(
        artifact,
        cache,
        cachePolicy,
        checksums = checksums,
        logger = logger,
        pool = pool
      ).leftMap(_.message).map { f =>
        // FIXME Catch error here?
        scala.io.Source.fromFile(f)("UTF-8").mkString
      }
  }

  lazy val ivy2Local = MavenRepository(
    new File(sys.props("user.home") + "/.ivy2/local/").toURI.toString,
    ivyLike = true
  )

  val defaultConcurrentDownloadCount = 6

  lazy val defaultPool =
    Executors.newFixedThreadPool(defaultConcurrentDownloadCount, Strategy.DefaultDaemonThreadFactory)


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
