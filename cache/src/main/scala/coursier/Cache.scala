package coursier

import java.math.BigInteger
import java.net.{ HttpURLConnection, URL, URLConnection, URLStreamHandler }
import java.nio.channels.{ OverlappingFileLockException, FileLock }
import java.nio.file.{ StandardCopyOption, Files => NioFiles }
import java.security.MessageDigest
import java.util.concurrent.{ ConcurrentHashMap, Executors, ExecutorService }
import java.util.regex.Pattern

import coursier.ivy.IvyRepository

import scala.annotation.tailrec

import scalaz._
import scalaz.Scalaz.ToEitherOps
import scalaz.concurrent.{ Task, Strategy }

import java.io.{ Serializable => _, _ }

object Cache {

  // Check SHA-1 if available, else be fine with no checksum
  val defaultChecksums = Seq(Some("SHA-1"), None)

  private val unsafeChars: Set[Char] = " %$&+,:;=?@<>#".toSet

  // Scala version of http://stackoverflow.com/questions/4571346/how-to-encode-url-to-avoid-special-characters-in-java/4605848#4605848
  // '/' was removed from the unsafe character list
  private def escape(input: String): String = {

    def toHex(ch: Int) =
      (if (ch < 10) '0' + ch else 'A' + ch - 10).toChar

    def isUnsafe(ch: Char) =
      ch > 128 || ch < 0 || unsafeChars(ch)

    input.flatMap {
      case ch if isUnsafe(ch) =>
        "%" + toHex(ch / 16) + toHex(ch % 16)
      case other =>
        other.toString
    }
  }

  private def withLocal(artifact: Artifact, cache: File): Artifact = {
    def local(url: String) =
      if (url.startsWith("file:///"))
        url.stripPrefix("file://")
      else if (url.startsWith("file:/"))
        url.stripPrefix("file:")
      else
        // FIXME Should we fully parse the URL here?
        // FIXME Should some safeguards be added against '..' components in paths?
        url.split(":", 2) match {
          case Array(protocol, remaining) =>
            val remaining0 =
              if (remaining.startsWith("///"))
                remaining.stripPrefix("///")
              else if (remaining.startsWith("/"))
                remaining.stripPrefix("/")
              else
                throw new Exception(s"URL $url doesn't contain an absolute path")

            new File(cache, escape(protocol + "/" + remaining0)) .toString

          case _ =>
            throw new Exception(s"No protocol found in URL $url")
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

  private def readFullyTo(
    in: InputStream,
    out: OutputStream,
    logger: Option[Logger],
    url: String,
    alreadyDownloaded: Long
  ): Unit = {

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

    helper(alreadyDownloaded)
  }

  private def withLockFor[T](file: File)(f: => FileError \/ T): FileError \/ T = {
    val lockFile = new File(file.getParentFile, s"${file.getName}.lock")

    lockFile.getParentFile.mkdirs()
    var out = new FileOutputStream(lockFile)

    try {
      var lock: FileLock = null
      try {
        lock = out.getChannel.tryLock()
        if (lock == null)
          -\/(FileError.Locked(file))
        else
          try f
          finally {
            lock.release()
            lock = null
            out.close()
            out = null
            lockFile.delete()
          }
      }
      catch {
        case e: OverlappingFileLockException =>
          -\/(FileError.Locked(file))
      }
      finally if (lock != null) lock.release()
    } finally if (out != null) out.close()
  }

  private def downloading[T](
    url: String,
    file: File,
    logger: Option[Logger]
  )(
    f: => FileError \/ T
  ): FileError \/ T =
    try {
      val o = new Object
      val prev = urlLocks.putIfAbsent(url, o)
      if (prev == null) {
        logger.foreach(_.downloadingArtifact(url, file))

        val res =
          try \/-(f)
          catch {
            case nfe: FileNotFoundException if nfe.getMessage != null =>
              logger.foreach(_.downloadedArtifact(url, success = false))
              -\/(-\/(FileError.NotFound(nfe.getMessage)))
            case e: Exception =>
              logger.foreach(_.downloadedArtifact(url, success = false))
              throw e
          }
          finally {
            urlLocks.remove(url)
          }

        for (res0 <- res)
          logger.foreach(_.downloadedArtifact(url, success = res0.isRight))

        res.merge
      } else
        -\/(FileError.ConcurrentDownload(url))
    }
    catch { case e: Exception =>
      -\/(FileError.DownloadError(s"Caught $e (${e.getMessage})"))
    }

  private def temporaryFile(file: File): File = {
    val dir = file.getParentFile
    val name = file.getName
    new File(dir, s"$name.part")
  }

  private val partialContentResponseCode = 206

  private val handlerClsCache = new ConcurrentHashMap[String, Option[URLStreamHandler]]

  private def handlerFor(url: String): Option[URLStreamHandler] = {
    val protocol = url.takeWhile(_ != ':')

    Option(handlerClsCache.get(protocol)) match {
      case None =>
        val clsName = s"coursier.cache.protocol.${protocol.capitalize}Handler"
        val clsOpt =
          try Some(Thread.currentThread().getContextClassLoader.loadClass(clsName))
          catch {
            case _: ClassNotFoundException =>
              None
          }

        def printError(e: Exception): Unit =
          scala.Console.err.println(
            s"Cannot instantiate $clsName: $e${Option(e.getMessage).map(" ("+_+")")}"
          )

        val handlerOpt = clsOpt.flatMap {
          cls =>
            try Some(cls.newInstance().asInstanceOf[URLStreamHandler])
            catch {
              case e: InstantiationException =>
                printError(e)
                None
              case e: IllegalAccessException =>
                printError(e)
                None
              case e: ClassCastException =>
                printError(e)
                None
            }
        }

        val prevOpt = Option(handlerClsCache.putIfAbsent(protocol, handlerOpt))
        prevOpt.getOrElse(handlerOpt)

      case Some(handlerOpt) =>
        handlerOpt
    }
  }

  /**
    * Returns a `java.net.URL` for `s`, possibly using the custom protocol handlers found under the
    * `coursier.cache.protocol` namespace.
    *
    * E.g. URL `"test://abc.com/foo"`, having protocol `"test"`, can be handled by a
    * `URLStreamHandler` named `coursier.cache.protocol.TestHandler` (protocol name gets
    * capitalized, and suffixed with `Handler` to get the class name).
    *
    * @param s
    * @return
    */
  def url(s: String): URL =
    new URL(null, s, handlerFor(s).orNull)

  private def download(
    artifact: Artifact,
    cache: File,
    checksums: Set[String],
    cachePolicy: CachePolicy,
    pool: ExecutorService,
    logger: Option[Logger] = None
  ): Task[Seq[((File, String), FileError \/ Unit)]] = {

    implicit val pool0 = pool

    val artifact0 = withLocal(artifact, cache)
      .extra
      .getOrElse("local", artifact)

    // Reference file - if it exists, and we get not found errors on some URLs, we assume
    // we can keep track of these missing, and not try to get them again later.
    val referenceFileOpt = {
      val referenceOpt = artifact.extra.get("metadata").map(withLocal(_, cache))
      val referenceOpt0 = referenceOpt.map(a => a.extra.getOrElse("local", a))

      referenceOpt0.map(a => new File(a.url))
    }

    def referenceFileExists: Boolean = referenceFileOpt.exists(_.exists())

    val pairs =
      Seq(artifact0.url -> artifact.url) ++ {
        checksums
          .intersect(artifact0.checksumUrls.keySet)
          .intersect(artifact.checksumUrls.keySet)
          .toSeq
          .map(sumType => artifact0.checksumUrls(sumType) -> artifact.checksumUrls(sumType))
      }

    def urlConn(url0: String) = {
      val conn = url(url0).openConnection() // FIXME Should this be closed?
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

    def urlLastModified(
      url: String,
      currentLastModifiedOpt: Option[Long], // for the logger
      logger: Option[Logger]
    ): EitherT[Task, FileError, Option[Long]] =
      EitherT {
        Task {
          urlConn(url) match {
            case c: HttpURLConnection =>
              logger.foreach(_.checkingUpdates(url, currentLastModifiedOpt))

              var success = false
              try {
                c.setRequestMethod("HEAD")
                val remoteLastModified = c.getLastModified

                // TODO 404 Not found could be checked here

                val res =
                  if (remoteLastModified > 0L)
                    Some(remoteLastModified)
                  else
                    None

                success = true
                logger.foreach(_.checkingUpdatesResult(url, currentLastModifiedOpt, res))

                res.right
              } finally {
                if (!success)
                  logger.foreach(_.checkingUpdatesResult(url, currentLastModifiedOpt, None))
              }

            case other =>
              -\/(FileError.DownloadError(s"Cannot do HEAD request with connection $other ($url)"))
          }
        }
      }

    def fileExists(file: File): Task[Boolean] =
      Task {
        file.exists()
      }

    def shouldDownload(file: File, url: String): EitherT[Task, FileError, Boolean] = {
      def check = for {
        fileLastModOpt <- fileLastModified(file)
        urlLastModOpt <- urlLastModified(url, fileLastModOpt, logger)
      } yield {
        val fromDatesOpt = for {
          fileLastMod <- fileLastModOpt
          urlLastMod <- urlLastModOpt
        } yield fileLastMod < urlLastMod

        fromDatesOpt.getOrElse(true)
      }

      EitherT {
        fileExists(file).flatMap {
          case false =>
            Task.now(true.right)
          case true =>
            check.run
        }
      }
    }

    def is404(conn: URLConnection) =
      conn match {
        case conn0: HttpURLConnection =>
          conn0.getResponseCode == 404
        case _ =>
          false
      }

    def remote(file: File, url: String): EitherT[Task, FileError, Unit] =
      EitherT {
        Task {
          withLockFor(file) {
            downloading(url, file, logger) {
              val tmp = temporaryFile(file)

              val alreadyDownloaded = tmp.length()

              val conn0 = urlConn(url)

              val (partialDownload, conn) = conn0 match {
                case conn0: HttpURLConnection if alreadyDownloaded > 0L =>
                  conn0.setRequestProperty("Range", s"bytes=$alreadyDownloaded-")

                  if (conn0.getResponseCode == partialContentResponseCode) {
                    val ackRange = Option(conn0.getHeaderField("Content-Range")).getOrElse("")

                    if (ackRange.startsWith(s"bytes $alreadyDownloaded-"))
                      (true, conn0)
                    else
                      // unrecognized Content-Range header -> start a new connection with no resume
                      (false, urlConn(url))
                  } else
                    (false, conn0)

                case _ => (false, conn0)
              }

              if (is404(conn))
                FileError.NotFound(url, permanent = Some(true)).left
              else {
                for (len0 <- Option(conn.getContentLengthLong) if len0 >= 0L) {
                  val len = len0 + (if (partialDownload) alreadyDownloaded else 0L)
                  logger.foreach(_.downloadLength(url, len, alreadyDownloaded))
                }

                val in = new BufferedInputStream(conn.getInputStream, bufferSize)

                val result =
                  try {
                    tmp.getParentFile.mkdirs()
                    val out = new FileOutputStream(tmp, partialDownload)
                    try \/-(readFullyTo(in, out, logger, url, if (partialDownload) alreadyDownloaded else 0L))
                    finally out.close()
                  } finally in.close()

                file.getParentFile.mkdirs()
                NioFiles.move(tmp.toPath, file.toPath, StandardCopyOption.ATOMIC_MOVE)

                for (lastModified <- Option(conn.getLastModified) if lastModified > 0L)
                  file.setLastModified(lastModified)

                result
              }
            }
          }
        }
      }

    def remoteKeepErrors(file: File, url: String): EitherT[Task, FileError, Unit] = {

      val errFile = new File(file.getParentFile, "." + file.getName + ".error")

      def validErrFileExists =
        EitherT {
          Task {
            (referenceFileExists && errFile.exists()).right[FileError]
          }
        }

      def createErrFile =
        EitherT {
          Task {
            if (referenceFileExists) {
              if (!errFile.exists())
                NioFiles.write(errFile.toPath, "".getBytes("UTF-8"))
            }

            ().right[FileError]
          }
        }

      def deleteErrFile =
        EitherT {
          Task {
            if (errFile.exists())
              errFile.delete()

            ().right[FileError]
          }
        }

      def retainError =
        EitherT {
          remote(file, url).run.flatMap {
            case err @ -\/(FileError.NotFound(_, Some(true))) =>
              createErrFile.run.map(_ => err)
            case other =>
              deleteErrFile.run.map(_ => other)
          }
        }

      cachePolicy match {
        case CachePolicy.FetchMissing | CachePolicy.LocalOnly =>
          validErrFileExists.flatMap { exists =>
            if (exists)
              EitherT(Task.now(FileError.NotFound(url, Some(true)).left[Unit]))
            else
              retainError
          }

        case CachePolicy.ForceDownload | CachePolicy.Update | CachePolicy.UpdateChanging =>
          retainError
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
            // for debug purposes, flaky with URL-encoded chars anyway
            // def filtered(s: String) =
            //   s.stripPrefix("file:/").stripPrefix("//").stripSuffix("/")
            // assert(
            //   filtered(url) == filtered(file.toURI.toString),
            //   s"URL: ${filtered(url)}, file: ${filtered(file.toURI.toString)}"
            // )
            checkFileExists(file, url)
          } else {
            def update = shouldDownload(file, url).flatMap {
              case true =>
                remoteKeepErrors(file, url)
              case false =>
                EitherT(Task.now[FileError \/ Unit](().right))
            }

            val cachePolicy0 = cachePolicy match {
              case CachePolicy.UpdateChanging if !artifact.changing =>
                CachePolicy.FetchMissing
              case other =>
                other
            }

            cachePolicy0 match {
              case CachePolicy.LocalOnly =>
                checkFileExists(file, url)
              case CachePolicy.UpdateChanging | CachePolicy.Update =>
                update
              case CachePolicy.FetchMissing =>
                checkFileExists(file, url) orElse remoteKeepErrors(file, url)
              case CachePolicy.ForceDownload =>
                remoteKeepErrors(file, url)
            }
          }


        res.run.map((file, url) -> _)
      }

    Nondeterminism[Task].gather(tasks)
  }

  def parseChecksum(content: String): Option[BigInteger] = {
    val lines = content
      .linesIterator
      .toVector

    parseChecksumLine(lines) orElse parseChecksumAlternative(lines)
  }

  // matches md5 or sha1
  private val checksumPattern = Pattern.compile("^[0-9a-f]{32}([0-9a-f]{8})?")

  private def findChecksum(elems: Seq[String]): Option[BigInteger] =
    elems.collectFirst {
      case rawSum if checksumPattern.matcher(rawSum).matches() =>
        new BigInteger(rawSum, 16)
    }

  private def parseChecksumLine(lines: Seq[String]): Option[BigInteger] =
    findChecksum(lines.map(_.toLowerCase.replaceAll("\\s", "")))

  private def parseChecksumAlternative(lines: Seq[String]): Option[BigInteger] =
    findChecksum(lines.flatMap(_.toLowerCase.split("\\s+")))

  def validateChecksum(
    artifact: Artifact,
    sumType: String,
    cache: File,
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
            val sumOpt = parseChecksum(
              new String(NioFiles.readAllBytes(new File(sumFile).toPath), "UTF-8")
            )

            sumOpt match {
              case None =>
                FileError.ChecksumFormatError(sumType, sumFile).left

              case Some(sum) =>
                val md = MessageDigest.getInstance(sumType)

                val f = new File(artifact0.url)
                val is = new FileInputStream(f)
                try withContent(is, md.update(_, 0, _))
                finally is.close()

                val digest = md.digest()
                val calculatedSum = new BigInteger(1, digest)

                if (sum == calculatedSum)
                  ().right
                else
                  FileError.WrongChecksum(
                    sumType,
                    calculatedSum.toString(16),
                    sum.toString(16),
                    artifact0.url,
                    sumFile
                  ).left
            }
          }

        case None =>
          Task.now(FileError.ChecksumNotFound(sumType, artifact0.url).left)
      }
    }
  }

  def file(
    artifact: Artifact,
    cache: File = default,
    cachePolicy: CachePolicy = CachePolicy.FetchMissing,
    checksums: Seq[Option[String]] = defaultChecksums,
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
    cache: File = default,
    cachePolicy: CachePolicy = CachePolicy.FetchMissing,
    checksums: Seq[Option[String]] = defaultChecksums,
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
        new String(NioFiles.readAllBytes(f.toPath), "UTF-8")
      }
  }

  private lazy val ivy2HomeUri = {
    // a bit touchy on Windows... - don't try to manually write down the URI with s"file://..."
    val str = new File(sys.props("user.home") + "/.ivy2/").toURI.toString
    if (str.endsWith("/"))
      str
    else
      str + "/"
  }

  lazy val ivy2Local = IvyRepository(
    ivy2HomeUri + "local/" +
      "[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/" +
      "[artifact](-[classifier]).[ext]",
    dropInfoAttributes = true
  )

  lazy val ivy2Cache = IvyRepository(
    ivy2HomeUri + "cache/" +
      "(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[organisation]/[module]/[type]s/[artifact]-[revision](-[classifier]).[ext]",
    metadataPatternOpt = Some(
      ivy2HomeUri + "cache/" +
        "(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[organisation]/[module]/[type]-[revision](-[classifier]).[ext]"
    ),
    withChecksums = false,
    withSignatures = false,
    dropInfoAttributes = true
  )

  lazy val default = new File(
    sys.env.getOrElse(
      "COURSIER_CACHE",
      sys.props("user.home") + "/.coursier/cache/v1"
    )
  ).getAbsoluteFile

  val defaultConcurrentDownloadCount = 6

  lazy val defaultPool =
    Executors.newFixedThreadPool(defaultConcurrentDownloadCount, Strategy.DefaultDaemonThreadFactory)


  private val urlLocks = new ConcurrentHashMap[String, Object]

  trait Logger {
    def foundLocally(url: String, f: File): Unit = {}

    def downloadingArtifact(url: String, file: File): Unit = {}

    @deprecated("Use / override the variant with 3 arguments instead")
    def downloadLength(url: String, length: Long): Unit = {}
    def downloadLength(url: String, totalLength: Long, alreadyDownloaded: Long): Unit = {
      downloadLength(url, totalLength)
    }

    def downloadProgress(url: String, downloaded: Long): Unit = {}

    def downloadedArtifact(url: String, success: Boolean): Unit = {}
    def checkingUpdates(url: String, currentTimeOpt: Option[Long]): Unit = {}
    def checkingUpdatesResult(url: String, currentTimeOpt: Option[Long], remoteTimeOpt: Option[Long]): Unit = {}
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

sealed abstract class FileError(val message: String) extends Product with Serializable

object FileError {

  final case class DownloadError(reason: String) extends FileError(s"Download error: $reason")

  final case class NotFound(file: String, permanent: Option[Boolean] = None) extends FileError(s"Not found: $file")

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
