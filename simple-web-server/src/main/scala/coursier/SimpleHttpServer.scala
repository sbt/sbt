package coursier

import java.io.{ File, FileOutputStream }
import java.net.NetworkInterface
import java.nio.channels.{ FileLock, OverlappingFileLockException }

import org.http4s.dsl._
import org.http4s.headers.Authorization
import org.http4s.server.HttpService
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.{ BasicCredentials, Challenge, EmptyBody, Request, Response }

import caseapp._

import scala.collection.JavaConverters._

import scalaz.concurrent.Task

case class SimpleHttpServerApp(
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
    realm: String
) extends App {

  val baseDir = new File(if (directory.isEmpty) "." else directory)

  val verbosityLevel = Tag.unwrap(verbose) - (if (quiet) 1 else 0)

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
        req.body.runLog.run.foreach { b =>
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

  val unauthorized = Unauthorized(Challenge("Basic", realm))

  def authenticated(pf: PartialFunction[Request, Task[Response]]): HttpService =
    authenticated0(HttpService(pf))

  def authenticated0(service: HttpService): HttpService =
    if (user.isEmpty && password.isEmpty)
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
                  if (basic.username == user && basic.password == password)
                    service.run(req).flatMap {
                      case Some(v) => Task.now(v)
                      case None => NotFound()
                    }
                  else {
                    warn {
                      val msg =
                        if (basic.username == user)
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

  def getService = authenticated {
    case (method @ (GET | HEAD)) -> path =>
      if (verbosityLevel >= 1)
        Console.err.println(s"${method.name} $path")

      val f = new File(baseDir, path.toList.mkString("/"))
      val resp = if (f.exists())
        Ok(f)
      else
        NotFound()

      method match {
        case HEAD =>
          resp.map(_.copy(body = EmptyBody))
        case _ =>
          resp
      }
  }

  val builder = {
    var b = BlazeBuilder.bindHttp(port, host)

    if (acceptWrite || acceptPut)
      b = b.mountService(putService)
    if (acceptWrite || acceptPost)
      b = b.mountService(postService)

    b = b.mountService(getService)

    b
  }

  if (verbosityLevel >= 0) {
    Console.err.println(s"Listening on http://$host:$port")

    if (verbosityLevel >= 1 && host == "0.0.0.0") {
      Console.err.println(s"Listening on addresses")
      for (itf <- NetworkInterface.getNetworkInterfaces.asScala; addr <- itf.getInetAddresses.asScala)
        Console.err.println(s"  ${addr.getHostAddress} (${itf.getName})")
    }
  }

  builder
    .run
    .awaitShutdown()

}

object SimpleHttpServer extends AppOf[SimpleHttpServerApp]
