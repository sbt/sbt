package coursier

import java.io.{File, FileOutputStream}
import java.net.NetworkInterface
import java.nio.channels.{FileLock, OverlappingFileLockException}

import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.{Authorization, `Content-Type`}
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.{Server, ServerApp}

import caseapp._
import caseapp.core.WithHelp

import scala.collection.JavaConverters._
import scalaz.concurrent.Task

final case class AuthOptions(
  @ExtraName("u")
  @ValueDescription("user")
    user: String = "",
  @ExtraName("P")
  @ValueDescription("password")
    password: String = "",
  @ExtraName("r")
  @ValueDescription("realm")
    realm: String = ""
) {
  def checks(): Unit = {
    if (user.nonEmpty && password.isEmpty)
      Console.err.println(
        "Warning: authentication enabled but no password specified. " +
          "Specify one with the --password or -P option."
      )

    if (password.nonEmpty && user.isEmpty)
      Console.err.println(
        "Warning: authentication enabled but no user specified. " +
          "Specify one with the --user or -u option."
      )

    if ((user.nonEmpty || password.nonEmpty) && realm.isEmpty)
      Console.err.println(
        "Warning: authentication enabled but no realm specified. " +
          "Specify one with the --realm or -r option."
      )
  }
}

final case class VerbosityOptions(
  @ExtraName("v")
    verbose: Int @@ Counter = Tag.of(0),
  @ExtraName("q")
    quiet: Boolean = false
) {
  lazy val verbosityLevel = Tag.unwrap(verbose) - (if (quiet) 1 else 0)
}

final case class HttpServerOptions(
  @Recurse
    auth: AuthOptions = AuthOptions(),
  @Recurse
    verbosity: VerbosityOptions = VerbosityOptions(),
  @ExtraName("d")
  @ValueDescription("served directory")
    directory: String = ".",
  @ExtraName("h")
  @ValueDescription("host")
    host: String = "0.0.0.0",
  @ExtraName("p")
  @ValueDescription("port")
    port: Int = 8080,
  @ExtraName("s")
    acceptPost: Boolean = false,
  @ExtraName("t")
    acceptPut: Boolean = false,
  @ExtraName("w")
  @HelpMessage("Accept write requests. Equivalent to -s -t")
    acceptWrite: Boolean = false,
  @ExtraName("l")
  @HelpMessage("Generate content listing pages for directories")
    listPages: Boolean = false
)

object HttpServer {

  def write(baseDir: File, path: Seq[String], req: Request): Boolean = {

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


  def unauthorized(realm: String) = Unauthorized(Challenge("Basic", realm))

  def authenticated0(options: AuthOptions, verbosityLevel: Int)(service: HttpService): HttpService =
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
              unauthorized(options.realm)
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
                    unauthorized(options.realm)
                  }
                case _ =>
                  warn("no basic credentials found")
                  unauthorized(options.realm)
              }
          }
      }

  def authenticated(options: AuthOptions, verbosityLevel: Int)(pf: PartialFunction[Request, Task[Response]]): HttpService =
    authenticated0(options, verbosityLevel)(HttpService(pf))

  def putService(baseDir: File, auth: AuthOptions, verbosityLevel: Int) =
    authenticated(auth, verbosityLevel) {
      case req @ PUT -> path =>
        if (verbosityLevel >= 1)
          Console.err.println(s"PUT $path")

        if (write(baseDir, path.toList, req))
          Ok()
        else
          Locked()
    }

  def postService(baseDir: File, auth: AuthOptions, verbosityLevel: Int) =
    authenticated(auth, verbosityLevel) {
      case req @ POST -> path =>
        if (verbosityLevel >= 1)
          Console.err.println(s"POST $path")

        if (write(baseDir, path.toList, req))
          Ok()
        else
          Locked()
    }

  def getService(baseDir: File, auth: AuthOptions, verbosityLevel: Int, listPages: Boolean) =
    authenticated(auth, verbosityLevel) {
      case (method @ (GET | HEAD)) -> path =>
        if (verbosityLevel >= 1)
          Console.err.println(s"${method.name} $path")

        val relPath = path.toList.mkString("/")
        val f = new File(baseDir, relPath)
        val resp =
          for {
            isDirOpt <- isDirectory(f)
            resp <- isDirOpt match {
              case Some(true) if listPages =>
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

  def server(options: HttpServerOptions): Task[Server] = {

    val baseDir = new File(if (options.directory.isEmpty) "." else options.directory)

    options.auth.checks()

    val verbosityLevel = options.verbosity.verbosityLevel

    val builder = {
      var b = BlazeBuilder.bindHttp(options.port, options.host)

      if (options.acceptWrite || options.acceptPut)
        b = b.mountService(putService(baseDir, options.auth, verbosityLevel))
      if (options.acceptWrite || options.acceptPost)
        b = b.mountService(postService(baseDir, options.auth, verbosityLevel))

      b = b.mountService(getService(baseDir, options.auth, verbosityLevel, options.listPages))

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

    builder.start
  }

}

object HttpServerApp extends ServerApp {

  val app: CaseApp[HttpServerOptions] =
    new CaseApp[HttpServerOptions] {
      def run(options: HttpServerOptions, args: RemainingArgs) =
        sys.error("unused")
    }

  def server(args: List[String]): Task[Server] =
    app.parser.withHelp.detailedParse(args) match {
      case Left(err) =>
        app.error(err)
        sys.error("unreached") // need to adjust return type of error method in CaseApp

      case Right((WithHelp(usage, help, t), remainingArgs, extraArgs)) =>

        if (help)
          app.helpAsked()

        if (usage)
          app.usageAsked()

        t match {
          case Left(err) =>
            app.error(err)
            sys.error("unreached")

          case Right(opts) =>

            val extraArgs0 = remainingArgs ++ extraArgs

            if (extraArgs0.nonEmpty)
              Console.err.println(
                s"Warning: ignoring extra arguments passed on the command-line (${extraArgs0.mkString(", ")})"
              )

            HttpServer.server(opts)
        }
    }

}
