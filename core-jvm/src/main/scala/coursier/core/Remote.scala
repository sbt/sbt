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
               classifier: String,
               `type`: String,
               cachePolicy: CachePolicy): EitherT[Task, String, File] = {

    val relPath =
      module.organization.split('.').toSeq ++ Seq(
        module.name,
        module.version,
        s"${module.name}-${module.version}${Some(classifier).filter(_.nonEmpty).map("-"+_).mkString}.${`type`}"
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
            val w = new FileOutputStream(file)
            try {
              @tailrec
              def helper(): Unit = {
                val read = in.read(b)
                if (read >= 0) {
                  w.write(b, 0, read)
                  helper()
                }
              }

              helper()
            } finally w.close()
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

  def artifact(dependency: Dependency,
               cachePolicy: CachePolicy = CachePolicy.Default): EitherT[Task, String, File] =
    artifact(dependency.module, dependency.classifier, dependency.`type`, cachePolicy = cachePolicy)

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
                  logger: Option[RemoteLogger] = None) extends Repository {

  def find(module: Module,
           cachePolicy: CachePolicy): EitherT[Task, String, Project] = {

    val relPath =
      module.organization.split('.').toSeq ++ Seq(
        module.name,
        module.version,
        s"${module.name}-${module.version}.pom"
      )

    def localFile = {
      for {
        cache0 <- cache.toRightDisjunction("No cache")
        f = (cache0 /: relPath)(new File(_, _))
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
      val urlStr = root + relPath.mkString("/")
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

    val task = cachePolicy.saving(locally)(remote)(save)
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

    val relPath =
      organization.split('.').toSeq ++ Seq(
        name,
        "maven-metadata.xml"
      )

    def locally = {
      ???
    }

    def remote = {
      val urlStr = root + relPath.mkString("/")
      val url = new URL(urlStr)

      Remote.readFully(url.openStream())
    }

    def save(s: String) = {
      // TODO
      Task.now(())
    }

    val task = cachePolicy.saving(locally)(remote)(save)
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
