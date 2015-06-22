package coursier
package core

import java.io._
import java.net.URL

import scala.annotation.tailrec
import scala.io.Codec
import scalaz._, Scalaz._
import scalaz.concurrent.Task

// FIXME This kind of side-effecting API is lame, we should aim at a more functional one.
trait ArtifactDownloaderLogger {
  def foundLocally(f: File): Unit
  def downloadingArtifact(url: String): Unit
  def downloadedArtifact(url: String, success: Boolean): Unit
}

case class ArtifactDownloader(root: String, cache: File, logger: Option[ArtifactDownloaderLogger] = None) {
  var bufferSize = 1024*1024

  def artifact(module: Module,
               version: String,
               artifact: Artifacts.Artifact,
               cachePolicy: CachePolicy): EitherT[Task, String, File] = {

    val relPath =
      module.organization.split('.').toSeq ++ Seq(
        module.name,
        version,
        s"${module.name}-$version${Some(artifact.classifier).filter(_.nonEmpty).map("-"+_).mkString}.${artifact.`type`}"
      )

    val file = (cache /: relPath)(new File(_, _))

    def locally = {
      Task {
        if (file.exists()) {
          logger.foreach(_.foundLocally(file))
          \/-(file)
        }
        else -\/("Not found in cache")
      }
    }

    def remote = {
      // FIXME A lot of things can go wrong here and are not properly handled:
      //  - checksums should be validated
      //  - what if the connection gets closed during the transfer (partial file on disk)?
      //  - what if someone is trying to write this file at the same time? (no locking of any kind yet)
      //  - ...

      val urlStr = root + relPath.mkString("/")

      Task {
        try {
          file.getParentFile.mkdirs()

          logger.foreach(_.downloadingArtifact(urlStr))

          val url = new URL(urlStr)
          val b = Array.fill[Byte](bufferSize)(0)
          val in = new BufferedInputStream(url.openStream(), bufferSize)

          try {
            val out = new FileOutputStream(file)
            try {
              @tailrec
              def helper(): Unit = {
                val read = in.read(b)
                if (read >= 0) {
                  out.write(b, 0, read)
                  helper()
                }
              }

              helper()
            } finally out.close()
          } finally in.close()

          logger.foreach(_.downloadedArtifact(urlStr, success = true))
          \/-(file)
        }
        catch { case e: Exception =>
          logger.foreach(_.downloadedArtifact(urlStr, success = false))
          -\/(e.getMessage)
        }
      }
    }

    EitherT(cachePolicy(locally)(remote))
  }

  def artifacts(dependency: Dependency,
                project: Project,
                cachePolicy: CachePolicy = CachePolicy.Default): Task[Seq[String \/ File]] = {

    val artifacts0 =
      dependency.artifacts match {
        case s: Artifacts.Sufficient => s.artifacts
        case p: Artifacts.WithProject => p.artifacts(project)
      }

    val tasks =
      artifacts0 .map { artifact0 =>
        // Important: using version from project, as the one from dependency can be an interval
        artifact(dependency.module, project.version, artifact0, cachePolicy = cachePolicy).run
      }

    Task.gatherUnordered(tasks)
  }

}

// FIXME Comment of ArtifactDownloaderLogger applies here too
trait RemoteLogger {
  def downloading(url: String): Unit
  def downloaded(url: String, success: Boolean): Unit
  def readingFromCache(f: File): Unit
  def puttingInCache(f: File): Unit
}

object Remote {

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
          try readFullySync(is)
          finally is0.close()

        new String(b, "UTF-8")
      } .leftMap(_.getMessage)
    }

}

case class Remote(root: String,
                  cache: Option[File] = None,
                  logger: Option[RemoteLogger] = None) extends MavenRepository {

  private def get(path: Seq[String],
                  cachePolicy: CachePolicy): EitherT[Task, String, String] = {

    lazy val localFile = {
      for {
        cache0 <- cache.toRightDisjunction("No cache")
        f = (cache0 /: path)(new File(_, _))
      } yield f
    }

    def locally = {
      Task {
        for {
          f0 <- localFile
          f <- Some(f0).filter(_.exists()).toRightDisjunction("Not found in cache")
          content <- \/.fromTryCatchNonFatal{
            logger.foreach(_.readingFromCache(f))
            scala.io.Source.fromFile(f)(Codec.UTF8).mkString
          }.leftMap(_.getMessage)
        } yield content
      }
    }

    def remote = {
      val urlStr = root + path.mkString("/")
      val url = new URL(urlStr)

      def log = Task(logger.foreach(_.downloading(urlStr)))
      def get = Remote.readFully(url.openStream())

      log.flatMap(_ => get)
    }

    def save(s: String) = {
      localFile.fold(_ => Task.now(()), f =>
        Task {
          if (!f.exists()) {
            logger.foreach(_.puttingInCache(f))
            f.getParentFile.mkdirs()
            val w = new PrintWriter(f)
            try w.write(s)
            finally w.close()
            ()
          }
        }
      )
    }

    EitherT(cachePolicy.saving(locally)(remote)(save))
  }

  def findNoInterval(module: Module,
                     version: String,
                     cachePolicy: CachePolicy): EitherT[Task, String, Project] = {

    val path =
      module.organization.split('.').toSeq ++ Seq(
        module.name,
        version,
        s"${module.name}-$version.pom"
      )

    val task = get(path, cachePolicy).run
      .map(eitherStr =>
        for {
          str <- eitherStr
          xml <- \/.fromEither(compatibility.xmlParse(str))
          _ <- if (xml.label == "project") \/-(()) else -\/("Project definition not found")
          proj <- Xml.project(xml)
        } yield proj
      )

    EitherT(task)
  }

  def versions(organization: String,
               name: String,
               cachePolicy: CachePolicy): EitherT[Task, String, Versions] = {

    val path =
      organization.split('.').toSeq ++ Seq(
        name,
        "maven-metadata.xml"
      )

    val task = get(path, cachePolicy).run
      .map(eitherStr =>
        for {
          str <- eitherStr
          xml <- \/.fromEither(compatibility.xmlParse(str))
          _ <- if (xml.label == "metadata") \/-(()) else -\/("Metadata not found")
          versions <- Xml.versions(xml)
        } yield versions
      )

    EitherT(task)
  }
}
