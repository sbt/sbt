package coursier

import java.math.BigInteger
import java.net.{HttpURLConnection, URL, URLConnection, URLStreamHandler, URLStreamHandlerFactory}
import java.nio.channels.{FileLock, OverlappingFileLockException}
import java.security.MessageDigest
import java.util.concurrent.{Callable, ConcurrentHashMap, ExecutorService, Executors}
import java.util.regex.Pattern

import coursier.core.Authentication
import coursier.ivy.IvyRepository
import coursier.internal.FileUtil

import scala.annotation.tailrec
import java.io.{Serializable => _, _}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, StandardCopyOption}
import java.util.Base64

import coursier.util.{EitherT, Schedulable}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Try
import scala.util.control.NonFatal

object Cache {

  private[coursier] def closeConn(conn: URLConnection): Unit = {
    Try(conn.getInputStream).toOption.filter(_ != null).foreach(_.close())
    conn match {
      case conn0: HttpURLConnection =>
        Try(conn0.getErrorStream).toOption.filter(_ != null).foreach(_.close())
        conn0.disconnect()
      case _ =>
    }
  }

  // Check SHA-1 if available, else be fine with no checksum
  val defaultChecksums = Seq(Some("SHA-1"), None)

  def localFile(url: String, cache: File, user: Option[String]): File =
    CachePath.localFile(url, cache, user.orNull)

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

  /**
    * Should be acquired when doing operations changing the file structure of the cache (creating
    * new directories, creating / acquiring locks, ...), so that these don't hinder each other.
    *
    * Should hopefully address some transient errors seen on the CI of ensime-server.
    */
  private def withStructureLock[T](cache: File)(f: => T): T =
    CachePath.withStructureLock(cache, new Callable[T] { def call() = f })

  private def withLockOr[T](
    cache: File,
    file: File
  )(
    f: => Either[FileError, T],
    ifLocked: => Option[Either[FileError, T]]
  ): Either[FileError, T] = {

    val lockFile = CachePath.lockFile(file)

    var out: FileOutputStream = null

    withStructureLock(cache) {
      lockFile.getParentFile.mkdirs()
      out = new FileOutputStream(lockFile)
    }

    @tailrec
    def loop(): Either[FileError, T] = {

      val resOpt = {
        var lock: FileLock = null
        try {
          lock = out.getChannel.tryLock()
          if (lock == null)
            ifLocked
          else
            try Some(f)
            finally {
              lock.release()
              lock = null
              out.close()
              out = null
              lockFile.delete()
            }
        }
        catch {
          case _: OverlappingFileLockException =>
            ifLocked
        }
        finally if (lock != null) lock.release()
      }

      resOpt match {
        case Some(res) => res
        case None =>
          loop()
      }
    }

    try loop()
    finally if (out != null) out.close()
  }

  def withLockFor[T](cache: File, file: File)(f: => Either[FileError, T]): Either[FileError, T] =
    withLockOr(cache, file)(f, Some(Left(FileError.Locked(file))))


  private def defaultRetryCount = 3

  private lazy val retryCount =
    sys.props
      .get("coursier.sslexception-retry")
      .flatMap(s => scala.util.Try(s.toInt).toOption)
      .filter(_ >= 0)
      .getOrElse(defaultRetryCount)

  private def downloading[T](
    url: String,
    file: File,
    logger: Option[Logger],
    retry: Int = retryCount
  )(
    f: => Either[FileError, T]
  ): Either[FileError, T] = {

    @tailrec
    def helper(retry: Int): Either[FileError, T] = {

      val resOpt =
        try {
          val o = new Object
          val prev = urlLocks.putIfAbsent(url, o)

          val res =
            if (prev == null)
              try f
              catch {
                case nfe: FileNotFoundException if nfe.getMessage != null =>
                  Left(FileError.NotFound(nfe.getMessage))
              }
              finally {
                urlLocks.remove(url)
              }
            else
              Left(FileError.ConcurrentDownload(url))

          Some(res)
        }
        catch {
          case _: javax.net.ssl.SSLException if retry >= 1 =>
            // TODO If Cache is made an (instantiated) class at some point, allow to log that exception.
            None
          case NonFatal(e) =>
            Some(Left(
              FileError.DownloadError(
                s"Caught $e${Option(e.getMessage).fold("")(" (" + _ + ")")} while downloading $url"
              )
            ))
        }

      resOpt match {
        case Some(res) => res
        case None =>
          helper(retry - 1)
      }
    }

    helper(retry)
  }

  private val partialContentResponseCode = 206
  private val invalidPartialContentResponseCode = 416

  private val handlerClsCache = new ConcurrentHashMap[String, Option[URLStreamHandler]]

  private def handlerFor(url: String): Option[URLStreamHandler] = {
    val protocol = url.takeWhile(_ != ':')

    Option(handlerClsCache.get(protocol)) match {
      case None =>
        val clsName = s"coursier.cache.protocol.${protocol.capitalize}Handler"
        def clsOpt(loader: ClassLoader): Option[Class[_]] =
          try Some(loader.loadClass(clsName))
          catch {
            case _: ClassNotFoundException =>
              None
          }

        val clsOpt0: Option[Class[_]] = clsOpt(Thread.currentThread().getContextClassLoader)
          .orElse(clsOpt(getClass.getClassLoader))

        def printError(e: Exception): Unit =
          scala.Console.err.println(
            s"Cannot instantiate $clsName: $e${Option(e.getMessage).fold("")(" ("+_+")")}"
          )

        val handlerFactoryOpt = clsOpt0.flatMap {
          cls =>
            try Some(cls.newInstance().asInstanceOf[URLStreamHandlerFactory])
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

        val handlerOpt = handlerFactoryOpt.flatMap {
          factory =>
            try Some(factory.createURLStreamHandler(protocol))
            catch {
              case NonFatal(e) =>
                scala.Console.err.println(
                  s"Cannot get handler for $protocol from $clsName: $e${Option(e.getMessage).fold("")(" ("+_+")")}"
                )
                None
            }
        }

        val prevOpt = Option(handlerClsCache.putIfAbsent(protocol, handlerOpt))
        prevOpt.getOrElse(handlerOpt)

      case Some(handlerOpt) =>
        handlerOpt
    }
  }

  private val BasicRealm = (
    "^" +
      Pattern.quote("Basic realm=\"") +
      "([^" + Pattern.quote("\"") + "]*)" +
      Pattern.quote("\"") +
    "$"
  ).r

  private def basicAuthenticationEncode(user: String, password: String): String =
    Base64.getEncoder.encodeToString(
      s"$user:$password".getBytes(UTF_8)
    )

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

  def urlConnection(url0: String, authentication: Option[Authentication]) = {
    var conn: URLConnection = null

    try {
      conn = url(url0).openConnection() // FIXME Should this be closed?
      // Dummy user-agent instead of the default "Java/...",
      // so that we are not returned incomplete/erroneous metadata
      // (Maven 2 compatibility? - happens for snapshot versioning metadata)
      conn.setRequestProperty("User-Agent", "")

      for (auth <- authentication)
        conn match {
          case authenticated: AuthenticatedURLConnection =>
            authenticated.authenticate(auth)
          case conn0: HttpURLConnection =>
            conn0.setRequestProperty(
              "Authorization",
              "Basic " + basicAuthenticationEncode(auth.user, auth.password)
            )
          case _ =>
          // FIXME Authentication is ignored
        }

      conn
    } catch {
      case NonFatal(e) =>
        if (conn != null)
          closeConn(conn)
        throw e
    }
  }

  private def contentLength(
    url: String,
    authentication: Option[Authentication],
    logger: Option[Logger]
  ): Either[FileError, Option[Long]] = {

    var conn: URLConnection = null

    try {
      conn = urlConnection(url, authentication)

      conn match {
        case c: HttpURLConnection =>
          logger.foreach(_.gettingLength(url))

          var success = false
          try {
            c.setRequestMethod("HEAD")
            val len = Some(c.getContentLengthLong)
              .filter(_ >= 0L)

            // TODO 404 Not found could be checked here

            success = true
            logger.foreach(_.gettingLengthResult(url, len))

            Right(len)
          } finally {
            if (!success)
              logger.foreach(_.gettingLengthResult(url, None))
          }

        case other =>
          Left(FileError.DownloadError(s"Cannot do HEAD request with connection $other ($url)"))
      }
    } finally {
      if (conn != null)
        closeConn(conn)
    }
  }

  private def download[F[_]](
    artifact: Artifact,
    cache: File,
    checksums: Set[String],
    cachePolicy: CachePolicy,
    pool: ExecutorService,
    logger: Option[Logger],
    ttl: Option[Duration]
  )(implicit S: Schedulable[F]): F[Seq[((File, String), Either[FileError, Unit])]] = {

    // Reference file - if it exists, and we get not found errors on some URLs, we assume
    // we can keep track of these missing, and not try to get them again later.
    val referenceFileOpt = artifact
      .extra
      .get("metadata")
      .map(a => localFile(a.url, cache, a.authentication.map(_.user)))

    def referenceFileExists: Boolean = referenceFileOpt.exists(_.exists())

    def fileLastModified(file: File): EitherT[F, FileError, Option[Long]] =
      EitherT {
        S.schedule(pool) {
          Right {
            val lastModified = file.lastModified()
            if (lastModified > 0L)
              Some(lastModified)
            else
              None
          } : Either[FileError, Option[Long]]
        }
      }

    def urlLastModified(
      url: String,
      currentLastModifiedOpt: Option[Long], // for the logger
      logger: Option[Logger]
    ): EitherT[F, FileError, Option[Long]] =
      EitherT {
        S.schedule(pool) {
          var conn: URLConnection = null

          try {
            conn = urlConnection(url, artifact.authentication)

            conn match {
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

                  Right(res)
                } finally {
                  if (!success)
                    logger.foreach(_.checkingUpdatesResult(url, currentLastModifiedOpt, None))
                }

              case other =>
                Left(FileError.DownloadError(s"Cannot do HEAD request with connection $other ($url)"))
            }
          } finally {
            if (conn != null)
              closeConn(conn)
          }
        }
      }

    def fileExists(file: File): F[Boolean] =
      S.schedule(pool) {
        file.exists()
      }

    def ttlFile(file: File): File =
      new File(file.getParent, s".${file.getName}.checked")

    def lastCheck(file: File): F[Option[Long]] = {

      val ttlFile0 = ttlFile(file)

      S.schedule(pool) {
        if (ttlFile0.exists())
          Some(ttlFile0.lastModified()).filter(_ > 0L)
        else
          None
      }
    }

    /** Not wrapped in a `Task` !!! */
    def doTouchCheckFile(file: File): Unit = {
      val ts = System.currentTimeMillis()
      val f = ttlFile(file)
      if (f.exists())
        f.setLastModified(ts)
      else {
        val fos = new FileOutputStream(f)
        fos.write(Array.empty[Byte])
        fos.close()
      }
    }

    def shouldDownload(file: File, url: String): EitherT[F, FileError, Boolean] = {

      def checkNeeded = ttl.fold(S.point(true)) { ttl =>
        if (ttl.isFinite())
          S.bind(lastCheck(file)) {
            case None => S.point(true)
            case Some(ts) =>
              S.map(S.schedule(pool)(System.currentTimeMillis()))(_ > ts + ttl.toMillis)
          }
        else
          S.point(false)
      }

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
        S.bind(fileExists(file)) {
          case false =>
            S.point(Right(true))
          case true =>
            S.bind(checkNeeded) {
              case false =>
                S.point(Right(false))
              case true =>
                S.bind(check.run) {
                  case Right(false) =>
                    S.schedule(pool) {
                      doTouchCheckFile(file)
                      Right(false)
                    }
                  case other =>
                    S.point(other)
                }
            }
        }
      }
    }

    def responseCode(conn: URLConnection): Option[Int] =
      conn match {
        case conn0: HttpURLConnection =>
          Some(conn0.getResponseCode)
        case _ =>
          None
      }

    def realm(conn: URLConnection): Option[String] =
      conn match {
        case conn0: HttpURLConnection =>
          Option(conn0.getHeaderField("WWW-Authenticate")).collect {
            case BasicRealm(realm) => realm
          }
        case _ =>
          None
      }

    def remote(
      file: File,
      url: String
    ): EitherT[F, FileError, Unit] =
      EitherT {
        S.schedule(pool) {

          val tmp = CachePath.temporaryFile(file)

          var lenOpt = Option.empty[Option[Long]]

          def doDownload(): Either[FileError, Unit] =
            downloading(url, file, logger) {

              val alreadyDownloaded = tmp.length()

              var conn: URLConnection = null

              try {
                conn = urlConnection(url, artifact.authentication)

                val partialDownload = conn match {
                  case conn0: HttpURLConnection if alreadyDownloaded > 0L =>
                    conn0.setRequestProperty("Range", s"bytes=$alreadyDownloaded-")

                    ((conn0.getResponseCode == partialContentResponseCode)
                       || (conn0.getResponseCode == invalidPartialContentResponseCode)) && {
                      val ackRange = Option(conn0.getHeaderField("Content-Range")).getOrElse("")

                      ackRange.startsWith(s"bytes $alreadyDownloaded-") || {
                        // unrecognized Content-Range header -> start a new connection with no resume
                        closeConn(conn)
                        conn = urlConnection(url, artifact.authentication)
                        false
                      }
                    }
                  case _ => false
                }

                if (responseCode(conn) == Some(404))
                  Left(FileError.NotFound(url, permanent = Some(true)))
                else if (responseCode(conn) == Some(401))
                  Left(FileError.Unauthorized(url, realm = realm(conn)))
                else {
                  for (len0 <- Option(conn.getContentLengthLong) if len0 >= 0L) {
                    val len = len0 + (if (partialDownload) alreadyDownloaded else 0L)
                    logger.foreach(_.downloadLength(url, len, alreadyDownloaded, watching = false))
                  }

                  val in = new BufferedInputStream(conn.getInputStream, bufferSize)

                  val result =
                    try {
                      val out = withStructureLock(cache) {
                        tmp.getParentFile.mkdirs()
                        new FileOutputStream(tmp, partialDownload)
                      }
                      try readFullyTo(in, out, logger, url, if (partialDownload) alreadyDownloaded else 0L)
                      finally out.close()
                    } finally in.close()

                  withStructureLock(cache) {
                    file.getParentFile.mkdirs()
                    Files.move(tmp.toPath, file.toPath, StandardCopyOption.ATOMIC_MOVE)
                  }

                  for (lastModified <- Option(conn.getLastModified) if lastModified > 0L)
                    file.setLastModified(lastModified)

                  doTouchCheckFile(file)

                  Right(result)
                }
              } finally {
                if (conn != null)
                  closeConn(conn)
              }
            }

          def checkDownload(): Option[Either[FileError, Unit]] = {

            def progress(currentLen: Long): Unit =
              if (lenOpt.isEmpty) {
                lenOpt = Some(contentLength(url, artifact.authentication, logger).right.toOption.flatten)
                for (o <- lenOpt; len <- o)
                  logger.foreach(_.downloadLength(url, len, currentLen, watching = true))
              } else
                logger.foreach(_.downloadProgress(url, currentLen))

            def done(): Unit =
              if (lenOpt.isEmpty) {
                lenOpt = Some(contentLength(url, artifact.authentication, logger).right.toOption.flatten)
                for (o <- lenOpt; len <- o)
                  logger.foreach(_.downloadLength(url, len, len, watching = true))
              } else
                for (o <- lenOpt; len <- o)
                  logger.foreach(_.downloadProgress(url, len))

            if (file.exists()) {
              done()
              Some(Right(()))
            } else {
              // yes, Thread.sleep. 'tis our thread pool anyway.
              // (And the various resources make it not straightforward to switch to a more Task-based internal API here.)
              Thread.sleep(20L)

              val currentLen = tmp.length()

              if (currentLen == 0L && file.exists()) { // check again if file exists in case it was created in the mean time
                done()
                Some(Right(()))
              } else {
                progress(currentLen)
                None
              }
            }
          }

          logger.foreach(_.downloadingArtifact(url, file))

          var res: Either[FileError, Unit] = null

          try {
            res = withLockOr(cache, file)(
              doDownload(),
              checkDownload()
            )
          } finally {
            logger.foreach(_.downloadedArtifact(url, success = res != null && res.isRight))
          }

          res
        }
      }

    def errFile(file: File) = new File(file.getParentFile, "." + file.getName + ".error")

    def remoteKeepErrors(file: File, url: String): EitherT[F, FileError, Unit] = {

      val errFile0 = errFile(file)

      def validErrFileExists =
        EitherT {
          S.schedule[Either[FileError, Boolean]](pool) {
            Right(referenceFileExists && errFile0.exists())
          }
        }

      def createErrFile =
        EitherT {
          S.schedule[Either[FileError, Unit]](pool) {
            if (referenceFileExists) {
              if (!errFile0.exists())
                FileUtil.write(errFile0, "".getBytes(UTF_8))
            }

            Right(())
          }
        }

      def deleteErrFile =
        EitherT {
          S.schedule[Either[FileError, Unit]](pool) {
            if (errFile0.exists())
              errFile0.delete()

            Right(())
          }
        }

      def retainError =
        EitherT {
          S.bind(remote(file, url).run) {
            case err @ Left(FileError.NotFound(_, Some(true))) =>
              S.map(createErrFile.run)(_ => err: Either[FileError, Unit])
            case other =>
              S.map(deleteErrFile.run)(_ => other)
          }
        }

      cachePolicy match {
        case CachePolicy.FetchMissing | CachePolicy.LocalOnly | CachePolicy.LocalUpdate | CachePolicy.LocalUpdateChanging =>
          validErrFileExists.flatMap { exists =>
            if (exists)
              EitherT(S.point[Either[FileError, Unit]](Left(FileError.NotFound(url, Some(true)))))
            else
              retainError
          }

        case CachePolicy.ForceDownload | CachePolicy.Update | CachePolicy.UpdateChanging =>
          retainError
      }
    }

    def localInfo(file: File, url: String): EitherT[F, FileError, Boolean] = {

      val errFile0 = errFile(file)

      // memo-ized

      lazy val res: Either[FileError, Boolean] =
        if (file.exists())
          Right(true)
        else if (referenceFileExists && errFile0.exists())
          Left(FileError.NotFound(url, Some(true)): FileError)
        else
          Right(false)

      EitherT(S.schedule(pool)(res))
    }

    def checkFileExists(file: File, url: String, log: Boolean = true): EitherT[F, FileError, Unit] =
      EitherT {
        S.schedule(pool) {
          if (file.exists()) {
            logger.foreach(_.foundLocally(url, file))
            Right(())
          } else
            Left(FileError.NotFound(file.toString))
        }
      }

    val urls =
      artifact.url +: {
        checksums
          .toSeq
          .flatMap(artifact.checksumUrls.get)
      }

    val cachePolicy0 = cachePolicy match {
      case CachePolicy.UpdateChanging if !artifact.changing =>
        CachePolicy.FetchMissing
      case CachePolicy.LocalUpdateChanging if !artifact.changing =>
        CachePolicy.LocalOnly
      case other =>
        other
    }

    val requiredArtifactCheck = artifact.extra.get("required") match {
      case None =>
        EitherT(S.point[Either[FileError, Unit]](Right(())))
      case Some(required) =>
        cachePolicy0 match {
          case CachePolicy.LocalOnly | CachePolicy.LocalUpdateChanging | CachePolicy.LocalUpdate =>
            val file = localFile(required.url, cache, artifact.authentication.map(_.user))
            localInfo(file, required.url).flatMap {
              case true =>
                EitherT(S.point[Either[FileError, Unit]](Right(())))
              case false =>
                EitherT(S.point[Either[FileError, Unit]](Left(FileError.NotFound(file.toString))))
            }
          case _ =>
            EitherT(S.point[Either[FileError, Unit]](Right(())))
        }
    }

    val tasks =
      for (url <- urls) yield {
        val file = localFile(url, cache, artifact.authentication.map(_.user))

        def res =
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
                EitherT(S.point[Either[FileError, Unit]](Right(())))
            }

            cachePolicy0 match {
              case CachePolicy.LocalOnly =>
                checkFileExists(file, url)
              case CachePolicy.LocalUpdateChanging | CachePolicy.LocalUpdate =>
                checkFileExists(file, url, log = false).flatMap { _ =>
                  update
                }
              case CachePolicy.UpdateChanging | CachePolicy.Update =>
                update
              case CachePolicy.FetchMissing =>
                checkFileExists(file, url) orElse remoteKeepErrors(file, url)
              case CachePolicy.ForceDownload =>
                remoteKeepErrors(file, url)
            }
          }

        S.map(requiredArtifactCheck.flatMap(_ => res).run)((file, url) -> _)
      }

    S.gather(tasks)
  }

  def parseChecksum(content: String): Option[BigInteger] = {
    val lines = content
      .lines
      .toVector

    parseChecksumLine(lines) orElse parseChecksumAlternative(lines)
  }

  def parseRawChecksum(content: Array[Byte]): Option[BigInteger] =
    if (content.length == 16 || content.length == 20)
      Some(new BigInteger(content))
    else {
      val s = new String(content, UTF_8)
      val lines = s
        .lines
        .toVector

      parseChecksumLine(lines) orElse parseChecksumAlternative(lines)
    }

  // matches md5 or sha1 or sha-256
  private val checksumPattern = Pattern.compile("^[0-9a-f]{32}([0-9a-f]{8})?([0-9a-f]{24})?")

  private def findChecksum(elems: Seq[String]): Option[BigInteger] =
    elems.collectFirst {
      case rawSum if checksumPattern.matcher(rawSum).matches() =>
        new BigInteger(rawSum, 16)
    }

  private def parseChecksumLine(lines: Seq[String]): Option[BigInteger] =
    findChecksum(lines.map(_.toLowerCase.replaceAll("\\s", "")))

  private def parseChecksumAlternative(lines: Seq[String]): Option[BigInteger] =
    findChecksum(lines.flatMap(_.toLowerCase.split("\\s+"))) orElse {
      findChecksum(lines.map(_.toLowerCase
        .split("\\s+")
        .filter(_.matches("[0-9a-f]+"))
        .mkString))
    }

  def validateChecksum[F[_]](
    artifact: Artifact,
    sumType: String,
    cache: File,
    pool: ExecutorService
  )(implicit S: Schedulable[F]): EitherT[F, FileError, Unit] = {

    val localFile0 = localFile(artifact.url, cache, artifact.authentication.map(_.user))

    EitherT {
      artifact.checksumUrls.get(sumType) match {
        case Some(sumUrl) =>
          val sumFile = localFile(sumUrl, cache, artifact.authentication.map(_.user))

          S.schedule(pool) {
            val sumOpt = parseRawChecksum(FileUtil.readAllBytes(sumFile))

            sumOpt match {
              case None =>
                Left(FileError.ChecksumFormatError(sumType, sumFile.getPath))

              case Some(sum) =>
                val md = MessageDigest.getInstance(sumType)

                val is = new FileInputStream(localFile0)
                try withContent(is, md.update(_, 0, _))
                finally is.close()

                val digest = md.digest()
                val calculatedSum = new BigInteger(1, digest)

                if (sum == calculatedSum)
                  Right(())
                else
                  Left(FileError.WrongChecksum(
                    sumType,
                    calculatedSum.toString(16),
                    sum.toString(16),
                    localFile0.getPath,
                    sumFile.getPath
                  ))
            }
          }

        case None =>
          S.point[Either[FileError, Unit]](Left(FileError.ChecksumNotFound(sumType, localFile0.getPath)))
      }
    }
  }


  /**
    * This method computes the task needed to get a file.
    *
    * Retry only applies to [[coursier.FileError.WrongChecksum]].
    *
    * [[coursier.FileError.DownloadError]] is handled separately at [[downloading]]
    */
  def file[F[_]](
    artifact: Artifact,
    cache: File = default,
    cachePolicy: CachePolicy = CachePolicy.UpdateChanging,
    checksums: Seq[Option[String]] = defaultChecksums,
    logger: Option[Logger] = None,
    pool: ExecutorService = defaultPool,
    ttl: Option[Duration] = defaultTtl,
    retry: Int = 1
  )(implicit S: Schedulable[F]): EitherT[F, FileError, File] = {

    val checksums0 = if (checksums.isEmpty) Seq(None) else checksums

    val res = EitherT {
      S.map(download(
        artifact,
        cache,
        checksums = checksums0.collect { case Some(c) => c }.toSet,
        cachePolicy,
        pool,
        logger = logger,
        ttl = ttl
      )) { results =>
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
        res.right.flatMap { _ =>
          checksum match {
            case None =>
              // FIXME All the checksums should be in the error, possibly with their URLs
              //       from artifact.checksumUrls
              Left(FileError.ChecksumNotFound(checksums0.last.get, ""))
            case Some(c) => Right((f, c))
          }
        }
      }
    }

    res.flatMap {
      case (f, None) => EitherT(S.point[Either[FileError, File]](Right(f)))
      case (f, Some(c)) =>
        validateChecksum(artifact, c, cache, pool).map(_ => f)
    }.leftFlatMap {
      case err: FileError.WrongChecksum =>
        if (retry <= 0) {
          EitherT(S.point(Left(err)))
        }
        else {
          EitherT {
            S.schedule[Either[FileError, Unit]](pool) {
              val badFile = localFile(artifact.url, cache, artifact.authentication.map(_.user))
              badFile.delete()
              logger.foreach(_.removedCorruptFile(artifact.url, badFile, Some(err)))
              Right(())
            }
          }.flatMap {
            _ =>
              file(
                artifact,
                cache,
                cachePolicy,
                checksums,
                logger,
                pool,
                ttl,
                retry - 1
              )
          }
        }
      case err =>
        EitherT(S.point(Left(err)))
    }
  }

  def fetch[F[_]](
    cache: File = default,
    cachePolicy: CachePolicy = CachePolicy.UpdateChanging,
    checksums: Seq[Option[String]] = defaultChecksums,
    logger: Option[Logger] = None,
    pool: ExecutorService = defaultPool,
    ttl: Option[Duration] = defaultTtl
  )(implicit S: Schedulable[F]): Fetch.Content[F] = {
    artifact =>
      file(
        artifact,
        cache,
        cachePolicy,
        checksums = checksums,
        logger = logger,
        pool = pool,
        ttl = ttl
      ).leftMap(_.describe).flatMap { f =>

        def notFound(f: File) = Left(s"${f.getCanonicalPath} not found")

        def read(f: File) =
          try Right(new String(FileUtil.readAllBytes(f), UTF_8))
          catch {
            case NonFatal(e) =>
              Left(s"Could not read (file:${f.getCanonicalPath}): ${e.getMessage}")
          }

        val res = if (f.exists()) {
          if (f.isDirectory) {
            if (artifact.url.startsWith("file:")) {

              val elements = f.listFiles().map { c =>
                val name = c.getName
                val name0 = if (c.isDirectory)
                  name + "/"
                else
                  name

                s"""<li><a href="$name0">$name0</a></li>"""
              }.mkString

              val page =
                s"""<!DOCTYPE html>
                   |<html>
                   |<head></head>
                   |<body>
                   |<ul>
                   |$elements
                   |</ul>
                   |</body>
                   |</html>
                 """.stripMargin

              Right(page)
            } else {
              val f0 = new File(f, ".directory")

              if (f0.exists()) {
                if (f0.isDirectory)
                  Left(s"Woops: ${f.getCanonicalPath} is a directory")
                else
                  read(f0)
              } else
                notFound(f0)
            }
          } else
            read(f)
        } else
          notFound(f)

        EitherT(S.point[Either[String, String]](res))
      }
  }

  private lazy val ivy2HomeUri = {

    val path =
      sys.props.get("coursier.ivy.home")
        .orElse(sys.props.get("ivy.home"))
        .getOrElse(sys.props("user.home") + "/.ivy2/")

    // a bit touchy on Windows... - don't try to manually write down the URI with s"file://..."
    val str = new File(path).toURI.toString
    if (str.endsWith("/"))
      str
    else
      str + "/"
  }

  lazy val ivy2Local = IvyRepository.fromPattern(
    (ivy2HomeUri + "local/") +: coursier.ivy.Pattern.default,
    dropInfoAttributes = true
  )

  lazy val ivy2Cache = IvyRepository.parse(
    ivy2HomeUri + "cache/" +
      "(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[organisation]/[module]/[type]s/[artifact]-[revision](-[classifier]).[ext]",
    metadataPatternOpt = Some(
      ivy2HomeUri + "cache/" +
        "(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[organisation]/[module]/[type]-[revision](-[classifier]).[ext]"
    ),
    withChecksums = false,
    withSignatures = false,
    dropInfoAttributes = true
  ).right.getOrElse(
    throw new Exception("Cannot happen")
  )

  lazy val default: File = CachePath.defaultCacheDirectory()

  val defaultConcurrentDownloadCount = 6

  lazy val defaultPool = Schedulable.fixedThreadPool(defaultConcurrentDownloadCount)

  lazy val defaultTtl: Option[Duration] = {
    def fromString(s: String) =
      Try(Duration(s)).toOption

    val fromEnv = sys.env.get("COURSIER_TTL").flatMap(fromString)
    def fromProps = sys.props.get("coursier.ttl").flatMap(fromString)
    def default = 24.hours

    fromEnv
      .orElse(fromProps)
      .orElse(Some(default))
  }

  private val urlLocks = new ConcurrentHashMap[String, Object]

  trait Logger {
    def foundLocally(url: String, f: File): Unit = {}

    def downloadingArtifact(url: String, file: File): Unit = {}

    def downloadProgress(url: String, downloaded: Long): Unit = {}

    def downloadedArtifact(url: String, success: Boolean): Unit = {}
    def checkingUpdates(url: String, currentTimeOpt: Option[Long]): Unit = {}
    def checkingUpdatesResult(url: String, currentTimeOpt: Option[Long], remoteTimeOpt: Option[Long]): Unit = {}

    def downloadLength(url: String, totalLength: Long, alreadyDownloaded: Long, watching: Boolean): Unit = {}

    def gettingLength(url: String): Unit = {}
    def gettingLengthResult(url: String, length: Option[Long]): Unit = {}

    def removedCorruptFile(url: String, file: File, reason: Option[FileError]): Unit
  }

  var bufferSize = 1024*1024

  def withContent(is: InputStream, f: (Array[Byte], Int) => Unit): Unit = {
    val data = Array.ofDim[Byte](16384)

    var nRead = is.read(data, 0, data.length)
    while (nRead != -1) {
      f(data, nRead)
      nRead = is.read(data, 0, data.length)
    }
  }

}
