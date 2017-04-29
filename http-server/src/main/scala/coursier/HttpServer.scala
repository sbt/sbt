package coursier

import java.io.{File, FileOutputStream}
import java.net.NetworkInterface
import java.nio.channels.{FileLock, OverlappingFileLockException}

import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.{Authorization, `Content-Type`}
import org.http4s.server.blaze.BlazeBuilder

import caseapp._

import scala.collection.JavaConverters._
import scalaz.concurrent.Task

final case class HttpServerOptions(
  @ExtraName("d")
  @ValueDescription("served directory")
    directory: String,
  @ExtraName("h")
  @ValueDescription("host")
    host: String = "0.0.0.0",
  @ExtraName("p")
  @ValueDescription("port")
    port: Int = 8080,
  @ExtraName("s")
    acceptPost: Boolean,
  @ExtraName("t")
    acceptPut: Boolean,
  @ExtraName("w")
  @HelpMessage("Accept write requests. Equivalent to -s -t")
    acceptWrite: Boolean,
  @ExtraName("v")
    verbose: Int @@ Counter,
  @ExtraName("q")
    quiet: Boolean,
  @ExtraName("u")
  @ValueDescription("user")
    user: String,
  @ExtraName("P")
  @ValueDescription("password")
    password: String,
  @ExtraName("r")
  @ValueDescription("realm")
    realm: String,
  @ExtraName("l")
  @HelpMessage("Generate content listing pages for directories")
    listPages: Boolean
)

object HttpServer extends CaseApp[HttpServerOptions] {
  def run(options: HttpServerOptions, args: RemainingArgs): Unit = {

    val baseDir = new File(if (options.directory.isEmpty) "." else options.directory)

    val verbosityLevel = Tag.unwrap(options.verbose) - (if (options.quiet) 1 else 0)

    def write(path: Seq[String], req: Request): Boolean = {

      val f = new File(baseDir, path.toList.mkString("/"))
      f.getParentFile.mkdirs()

      var os: FileOutputStream = null
      var lock: FileLock = null
      try {
        os = new FileOutputStream(f)
        lock =
          try os.getChannel.tryLock()
          catch {
            case _: OverlappingFileLockException =>
              null
          }

        if (lock == null)
          false
        else {
          req.body.runLog.unsafePerformSync.foreach { b =>
            b.copyToStream(os)
          }

          true
        }
      } finally {
        if (lock != null)
          lock.release()
        if (os != null)
          os.close()
      }
    }

    if (options.user.nonEmpty && options.password.isEmpty)
      Console.err.println(
        "Warning: authentication enabled but no password specified. " +
        "Specify one with the --password or -P option."
      )

    if (options.password.nonEmpty && options.user.isEmpty)
      Console.err.println(
        "Warning: authentication enabled but no user specified. " +
          "Specify one with the --user or -u option."
      )

    if ((options.user.nonEmpty || options.password.nonEmpty) && options.realm.isEmpty)
      Console.err.println(
        "Warning: authentication enabled but no realm specified. " +
        "Specify one with the --realm or -r option."
      )

    val unauthorized = Unauthorized(Challenge("Basic", options.realm))

    def authenticated(pf: PartialFunction[Request, Task[Response]]): HttpService =
      authenticated0(HttpService(pf))

    def authenticated0(service: HttpService): HttpService =
      if (options.user.isEmpty && options.password.isEmpty)
        service
      else
        HttpService {
          case req =>
            def warn(msg: => String) =
              if (verbosityLevel >= 1)
                Console.err.println(s"${req.method.name} ${req.uri.path}: $msg")

            req.headers.get(Authorization) match {
              case None =>
                warn("no authentication provided")
                unauthorized
              case Some(auth) =>
                auth.credentials match {
                  case basic: BasicCredentials =>
                    if (basic.username == options.user && basic.password == options.password)
                      service.run(req)
                    else {
                      warn {
                        val msg =
                          if (basic.username == options.user)
                            "wrong password"
                          else
                            s"unrecognized user ${basic.username}"

                        s"authentication failed ($msg)"
                      }
                      unauthorized
                    }
                  case _ =>
                    warn("no basic credentials found")
                    unauthorized
                }
            }
        }

    def putService = authenticated {
      case req @ PUT -> path =>
        if (verbosityLevel >= 1)
          Console.err.println(s"PUT $path")

        if (write(path.toList, req))
          Ok()
        else
          Locked()
    }

    def postService = authenticated {
      case req @ POST -> path =>
        if (verbosityLevel >= 1)
          Console.err.println(s"POST $path")

        if (write(path.toList, req))
          Ok()
        else
          Locked()
    }

    def isDirectory(f: File): Task[Option[Boolean]] =
      Task {
        if (f.isDirectory)
          Some(true)
        else if (f.isFile)
          Some(false)
        else
          None
      }

    def directoryListingPage(dir: File, title: String): Task[String] =
      Task {
        val entries = dir
          .listFiles()
          .flatMap { f =>
            def name = f.getName
            if (f.isDirectory)
              Seq(name + "/")
            else if (f.isFile)
              Seq(name)
            else
              Nil
          }

        // meh escaping
        // TODO Use to scalatags to generate that
        s"""<!DOCTYPE html>
           |<html>
           |<head>
           |<title>$title</title>
           |</head>
           |<body>
           |<ul>
           |${entries.map(e => "  <li><a href=\"" + e + "\">" + e + "</a></li>").mkString("\n")}
           |</ul>
           |</body>
           |</html>
         """.stripMargin
      }

    def getService = authenticated {
      case (method @ (GET | HEAD)) -> path =>
        if (verbosityLevel >= 1)
          Console.err.println(s"${method.name} $path")

        val relPath = path.toList.mkString("/")
        val f = new File(baseDir, relPath)
        val resp =
          for {
            isDirOpt <- isDirectory(f)
            resp <- isDirOpt match {
              case Some(true) if options.listPages =>
                directoryListingPage(f, relPath).flatMap(page =>
                  Ok(page).withContentType(Some(`Content-Type`(MediaType.`text/html`)))
                )
              case Some(false) => Ok(f)
              case _ => NotFound()
            }
          } yield resp

        method match {
          case HEAD =>
            resp.map(_.copy(body = EmptyBody))
          case _ =>
            resp
        }
    }

    val builder = {
      var b = BlazeBuilder.bindHttp(options.port, options.host)

      if (options.acceptWrite || options.acceptPut)
        b = b.mountService(putService)
      if (options.acceptWrite || options.acceptPost)
        b = b.mountService(postService)

      b = b.mountService(getService)

      b
    }

    if (verbosityLevel >= 0) {
      Console.err.println(s"Listening on http://${options.host}:${options.port}")

      if (verbosityLevel >= 1 && options.host == "0.0.0.0") {
        Console.err.println(s"Listening on addresses")
        for (itf <- NetworkInterface.getNetworkInterfaces.asScala; addr <- itf.getInetAddresses.asScala)
          Console.err.println(s"  ${addr.getHostAddress} (${itf.getName})")
      }
    }

    builder
      .run
      .awaitShutdown()
  }

}
