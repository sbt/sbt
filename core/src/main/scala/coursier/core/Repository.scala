package coursier.core

import scalaz.{-\/, \/-, \/, EitherT}
import scalaz.concurrent.Task

import coursier.core.compatibility.encodeURIComponent

trait Repository {
  def find(module: Module, version: String, cachePolicy: CachePolicy = CachePolicy.Default): EitherT[Task, String, Project]
  def artifacts(dependency: Dependency, project: Project): Seq[Artifact]
}

sealed trait CachePolicy {
  def apply[E,T](local: => Task[E \/ T])(remote: => Task[E \/ T]): Task[E \/ T]

  def saving[E,T](local: => Task[E \/ T])(remote: => Task[E \/ T])(save: => T => Task[Unit]): Task[E \/ T] =
    apply(local)(CachePolicy.saving(remote)(save))
}

object CachePolicy {
  def saving[E,T](remote: => Task[E \/ T])(save: T => Task[Unit]): Task[E \/ T] = {
    for {
      res <- remote
      _ <- res.fold(_ => Task.now(()), t => save(t))
    } yield res
  }

  case object Default extends CachePolicy {
    def apply[E,T](local: => Task[E \/ T])(remote: => Task[E \/ T]): Task[E \/ T] =
      local.flatMap(res => if (res.isLeft) remote else Task.now(res))
  }
  case object LocalOnly extends CachePolicy {
    def apply[E,T](local: => Task[E \/ T])(remote: => Task[E \/ T]): Task[E \/ T] =
      local
  }
  case object ForceDownload extends CachePolicy {
    def apply[E,T](local: => Task[E \/ T])(remote: => Task[E \/ T]): Task[E \/ T] =
      remote
  }
}

object Repository {
  implicit class ArtifactExtensions(val underlying: Artifact) extends AnyVal {
    def withDefaultChecksums: Artifact =
      underlying.copy(extra = underlying.extra ++ Seq(
        Artifact.md5 -> (underlying.url + ".md5"),
        Artifact.sha1 -> (underlying.url + ".sha1")
      ))
    def withDefaultSignature: Artifact =
      underlying.copy(extra = underlying.extra ++ Seq(
        Artifact.sigMd5 -> (underlying.url + ".asc.md5"),
        Artifact.sigSha1 -> (underlying.url + ".asc.sha1"),
        Artifact.sig -> (underlying.url + ".asc")
      ))
    def withJavadocSources: Artifact = {
      val base = underlying.url.stripSuffix(".jar")
      underlying.copy(extra = underlying.extra ++ Seq(
        Artifact.sourcesMd5 -> (base + "-sources.jar.md5"),
        Artifact.sourcesSha1 -> (base + "-sources.jar.sha1"),
        Artifact.sources -> (base + "-sources.jar"),
        Artifact.sourcesSigMd5 -> (base + "-sources.jar.asc.md5"),
        Artifact.sourcesSigSha1 -> (base + "-sources.jar.asc.sha1"),
        Artifact.sourcesSig -> (base + "-sources.jar.asc"),
        Artifact.javadocMd5 -> (base + "-javadoc.jar.md5"),
        Artifact.javadocSha1 -> (base + "-javadoc.jar.sha1"),
        Artifact.javadoc -> (base + "-javadoc.jar"),
        Artifact.javadocSigMd5 -> (base + "-javadoc.jar.asc.md5"),
        Artifact.javadocSigSha1 -> (base + "-javadoc.jar.asc.sha1"),
        Artifact.javadocSig -> (base + "-javadoc.asc.jar")
      ))
    }
  }
}

trait FetchMetadata {
  def root: String
  def apply(artifact: Artifact,
            cachePolicy: CachePolicy): EitherT[Task, String, String]
}

case class MavenRepository[F <: FetchMetadata](fetchMetadata: F,
                                               ivyLike: Boolean = false) extends Repository {

  import Repository._

  def projectArtifact(module: Module, version: String): Artifact = {
    if (ivyLike) ???
    else {
      val path = (
        module.organization.split('.').toSeq ++ Seq(
          module.name,
          version,
          s"${module.name}-$version.pom"
        )
      ) .map(encodeURIComponent)

      Artifact(
        path.mkString("/"),
        Map(
          Artifact.md5 -> "",
          Artifact.sha1 -> ""
        ),
        Attributes("pom", "")
      )
      .withDefaultSignature
    }
  }

  def versionsArtifact(module: Module): Option[Artifact] =
    if (ivyLike) None
    else {
      val path = (
        module.organization.split('.').toSeq ++ Seq(
          module.name,
          "maven-metadata.xml"
        )
      ) .map(encodeURIComponent)

      val artifact =
        Artifact(
          path.mkString("/"),
          Map.empty,
          Attributes("pom", "")
        )
        .withDefaultChecksums

      Some(artifact)
    }

  def versions(module: Module,
               cachePolicy: CachePolicy = CachePolicy.Default): EitherT[Task, String, Versions] = {

    EitherT(
      versionsArtifact(module) match {
        case None => Task.now(-\/("Not supported"))
        case Some(artifact) =>
          fetchMetadata(artifact, cachePolicy)
            .run
            .map(eitherStr =>
              for {
                str <- eitherStr
                xml <- \/.fromEither(compatibility.xmlParse(str))
                _ <- if (xml.label == "metadata") \/-(()) else -\/("Metadata not found")
                versions <- Xml.versions(xml)
              } yield versions
            )
      }
    )
  }

  def findNoInterval(module: Module,
                     version: String,
                     cachePolicy: CachePolicy): EitherT[Task, String, Project] = {

    EitherT {
      fetchMetadata(projectArtifact(module, version), cachePolicy)
        .run
        .map(eitherStr =>
          for {
            str <- eitherStr
            xml <- \/.fromEither(compatibility.xmlParse(str))
            _ <- if (xml.label == "project") \/-(()) else -\/("Project definition not found")
            proj <- Xml.project(xml)
          } yield proj
        )
    }
  }

  def find(module: Module,
           version: String,
           cachePolicy: CachePolicy): EitherT[Task, String, Project] = {

    Parse.versionInterval(version).filter(_.isValid) match {
      case None => findNoInterval(module, version, cachePolicy)
      case Some(itv) =>
        versions(module, cachePolicy)
          .flatMap { versions0 =>
            val eitherVersion = {
              val release = Version(versions0.release)

              if (itv.contains(release)) \/-(versions0.release)
              else {
                val inInterval = versions0.available
                  .map(Version(_))
                  .filter(itv.contains)

                if (inInterval.isEmpty) -\/(s"No version found for $version")
                else \/-(inInterval.max.repr)
              }
            }

            eitherVersion match {
              case -\/(reason) => EitherT[Task, String, Project](Task.now(-\/(reason)))
              case \/-(version0) =>
                findNoInterval(module, version0, cachePolicy)
                  .map(_.copy(versions = Some(versions0)))
            }
          }
    }
  }

  def artifacts(dependency: Dependency,
                project: Project): Seq[Artifact] = {

    val path =
      dependency.module.organization.split('.').toSeq ++ Seq(
        dependency.module.name,
        project.version,
        s"${dependency.module.name}-${project.version}${Some(dependency.attributes.classifier).filter(_.nonEmpty).map("-"+_).mkString}.${dependency.attributes.`type`}"
      )

    var artifact =
      Artifact(
        fetchMetadata.root + path.mkString("/"),
        Map.empty,
        dependency.attributes
      )
      .withDefaultChecksums

    if (dependency.attributes.`type` == "jar")
      artifact = artifact
        .withDefaultSignature
        .withJavadocSources

    Seq(artifact)
  }
}