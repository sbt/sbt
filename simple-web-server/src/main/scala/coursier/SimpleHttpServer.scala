package coursier

import java.io.{ File, FileOutputStream }
import java.nio.channels.{ FileLock, OverlappingFileLockException }

import org.http4s.dsl._
import org.http4s.headers.Authorization
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.{ BasicCredentials, Challenge, HttpService, Request, Response }

import caseapp._

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
  @ExtraName("P")
    acceptPost: Boolean,
  @ExtraName("t")
    acceptPut: Boolean,
  @ExtraName("w")
  @HelpMessage("Accept write requests. Equivalent to -P -t")
    acceptWrite: Boolean,
  @ExtraName("v")
    verbose: Int @@ Counter,
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

  val verbosityLevel = Tag.unwrap(verbose)

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
          req.headers.get(Authorization) match {
            case None =>
              unauthorized
            case Some(auth) =>
              auth.credentials match {
                case basic: BasicCredentials =>
                  if (basic.username == user && basic.password == password)
                    service.run(req)
                  else
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
    case GET -> path =>
      if (verbosityLevel >= 1)
        Console.err.println(s"GET $path")

      val f = new File(baseDir, path.toList.mkString("/"))
      if (f.exists())
        Ok(f)
      else
        NotFound()
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

  builder
    .run
    .awaitShutdown()

}

object SimpleHttpServer extends AppOf[SimpleHttpServerApp]
