package coursier.test

import java.io.{FileNotFoundException, InputStream}
import java.nio.file.{Files, Paths}

import coursier.util.TestEscape
import coursier.{Cache, Fetch, Platform}

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{-\/, EitherT, \/, \/-}
import scalaz.concurrent.Task

package object compatibility {

  implicit val executionContext = scala.concurrent.ExecutionContext.global

  implicit class TaskExtensions[T](val underlying: Task[T]) extends AnyVal {
    def runF: Future[T] = Future.successful(underlying.unsafePerformSync)
  }

  def textResource(path: String)(implicit ec: ExecutionContext): Future[String] = Future {
    val res = Option(getClass.getClassLoader.getResource(path)).getOrElse {
      throw new Exception(s"Not found: resource $path")
    }
    val is = res.openStream()

    new String(Platform.readFullySync(is), "UTF-8")
  }

  private val baseRepo = {
    val dir = Paths.get("tests/metadata")
    assert(Files.isDirectory(dir))
    dir
  }

  private val fillChunks = sys.env.get("FILL_CHUNKS").exists(s => s == "1" || s == "true")

  val artifact: Fetch.Content[Task] = { artifact =>

    if (artifact.url.startsWith("file:/") || artifact.url.startsWith("http://localhost:"))
      EitherT(Platform.readFully(
        Cache.urlConnection(artifact.url, artifact.authentication).getInputStream
      ))
    else {

      assert(artifact.authentication.isEmpty)

      val path = baseRepo.resolve(TestEscape.urlAsPath(artifact.url))

      val init = EitherT[Task, String, Unit] {
        if (Files.exists(path))
          Task.now(\/-(()))
        else if (fillChunks)
          Task[String \/ Unit] {
            Files.createDirectories(path.getParent)
            def is() = Cache.urlConnection(artifact.url, artifact.authentication).getInputStream
            val b = Platform.readFullySync(is())
            Files.write(path, b)
            \/-(())
          }.handle {
            case e: Exception =>
              -\/(e.toString)
          }
        else
          Task.now(-\/(s"not found: $path"))
      }

      init.flatMap { _ =>
        EitherT(Platform.readFully(Files.newInputStream(path)))
      }
    }
  }

}
