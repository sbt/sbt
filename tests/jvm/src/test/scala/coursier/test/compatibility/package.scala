package coursier.test

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}

import coursier.util.{EitherT, Task, TestEscape}
import coursier.{Cache, Fetch, Platform}

import scala.concurrent.{ExecutionContext, Future}

package object compatibility {

  implicit val executionContext = scala.concurrent.ExecutionContext.global

  def textResource(path: String)(implicit ec: ExecutionContext): Future[String] = Future {
    val res = Option(getClass.getClassLoader.getResource(path)).getOrElse {
      throw new Exception(s"Not found: resource $path")
    }
    val is = res.openStream()

    new String(Platform.readFullySync(is), UTF_8)
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
          Task.point(Right(()))
        else if (fillChunks)
          Task.delay[Either[String, Unit]] {
            Files.createDirectories(path.getParent)
            def is() = Cache.urlConnection(artifact.url, artifact.authentication).getInputStream
            val b = Platform.readFullySync(is())
            Files.write(path, b)
            Right(())
          }.handle {
            case e: Exception =>
              Left(e.toString)
          }
        else
          Task.point(Left(s"not found: $path"))
      }

      init.flatMap { _ =>
        EitherT(Platform.readFully(Files.newInputStream(path)))
      }
    }
  }

  private lazy val baseResources = {
    val dir = Paths.get("tests/shared/src/test/resources")
    assert(Files.isDirectory(dir))
    dir
  }

  def tryCreate(path: String, content: String): Unit =
    if (fillChunks) {
      val path0 = baseResources.resolve(path)
      Files.createDirectories(path0.getParent)
      Files.write(path0, content.getBytes(UTF_8))
    }

}
