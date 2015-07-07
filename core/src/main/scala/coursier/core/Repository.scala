package coursier.core

import scalaz.{ -\/, \/-, \/, EitherT }
import scalaz.concurrent.Task

import coursier.core.compatibility.encodeURIComponent

trait Repository {
  def find(
    module: Module,
    version: String
  )(implicit
    cachePolicy: CachePolicy
  ): EitherT[Task, String, (Artifact.Source, Project)]
}

object Repository {

  val mavenCentral = MavenRepository("https://repo1.maven.org/maven2/")

  val sonatypeReleases = MavenRepository("https://oss.sonatype.org/content/repositories/releases/")
  val sonatypeSnapshots = MavenRepository("https://oss.sonatype.org/content/repositories/snapshots/")

  lazy val ivy2Local = MavenRepository("file://" + sys.props("user.home") + "/.ivy2/local/", ivyLike = true)


  /**
   * Try to find `module` among `repositories`.
   *
   * Look at `repositories` from the left, one-by-one, and stop at first success.
   * Else, return all errors, in the same order.
   *
   * The `version` field of the returned `Project` in case of success may not be
   * equal to the provided one, in case the latter is not a specific
   * version (e.g. version interval). Which version get chosen depends on
   * the repository implementation.
   */
  def find(
    repositories: Seq[Repository],
    module: Module,
    version: String
  )(implicit
    cachePolicy: CachePolicy
  ): EitherT[Task, Seq[String], (Artifact.Source, Project)] = {

    val lookups = repositories
      .map(repo => repo -> repo.find(module, version).run)

    val task = lookups
      .foldLeft(Task.now(-\/(Nil)): Task[Seq[String] \/ (Artifact.Source, Project)]) {
        case (acc, (repo, eitherProjTask)) =>
          acc
            .flatMap {
              case -\/(errors) =>
                eitherProjTask
                  .map(res => res
                    .flatMap{case (source, project) =>
                      if (project.module == module) \/-((source, project))
                      else -\/(s"Wrong module returned (expected: $module, got: ${project.module})")
                    }
                    .leftMap(error => error +: errors)
                  )

              case res @ \/-(_) =>
                Task.now(res)
            }
      }

    EitherT(task.map(_.leftMap(_.reverse)))
      .map {case x @ (_, proj) =>
        assert(proj.module == module)
        x
      }
  }

  implicit class ArtifactExtensions(val underlying: Artifact) extends AnyVal {
    def withDefaultChecksums: Artifact =
      underlying.copy(checksumUrls = underlying.checksumUrls ++ Seq(
        "MD5" -> (underlying.url + ".md5"),
        "SHA-1" -> (underlying.url + ".sha1")
      ))
    def withDefaultSignature: Artifact =
      underlying.copy(extra = underlying.extra ++ Seq(
        "sig" ->
          Artifact(underlying.url + ".asc", Map.empty, Map.empty, Attributes("asc", ""))
            .withDefaultChecksums
      ))
    def withJavadocSources: Artifact = {
      val base = underlying.url.stripSuffix(".jar")
      underlying.copy(extra = underlying.extra ++ Seq(
        "sources" -> Artifact(base + "-sources.jar", Map.empty, Map.empty, Attributes("jar", "src")) // Are these the right attributes?
          .withDefaultChecksums
          .withDefaultSignature,
        "javadoc" -> Artifact(base + "-javadoc.jar", Map.empty, Map.empty, Attributes("jar", "javadoc")) // Same comment as above
          .withDefaultChecksums
          .withDefaultSignature
      ))
    }
  }
}

case class MavenSource(root: String, ivyLike: Boolean) extends Artifact.Source {
  import Repository._
  import BaseMavenRepository._

  def artifacts(
    dependency: Dependency,
    project: Project
  ): Seq[Artifact] = {

    def ivyLikePath0(subDir: String, baseSuffix: String, ext: String) =
      ivyLikePath(
        dependency.module.organization,
        dependency.module.name,
        project.version,
        subDir,
        baseSuffix,
        ext
      )

    val path =
      if (ivyLike)
        ivyLikePath0(dependency.attributes.`type` + "s", "", dependency.attributes.`type`)
      else {
        val versioning =
          project
            .snapshotVersioning
            .flatMap(versioning =>
              mavenVersioning(versioning, dependency.attributes.classifier, dependency.attributes.`type`)
            )

        dependency.module.organization.split('.').toSeq ++ Seq(
          dependency.module.name,
          project.version,
          s"${dependency.module.name}-${versioning getOrElse project.version}${Some(dependency.attributes.classifier).filter(_.nonEmpty).map("-"+_).mkString}.${dependency.attributes.`type`}"
        )
      }

    var artifact =
      Artifact(
        root + path.mkString("/"),
        Map.empty,
        Map.empty,
        dependency.attributes
      )
      .withDefaultChecksums

    if (dependency.attributes.`type` == "jar") {
      artifact = artifact.withDefaultSignature

      // FIXME Snapshot versioning of sources and javadoc is not taken into account here.
      // Will be ok if it's the same as the main JAR though.

      artifact =
        if (ivyLike) {
          val srcPath = root + ivyLikePath0("srcs", "-sources", "jar").mkString("/")
          val javadocPath = root + ivyLikePath0("docs", "-javadoc", "jar").mkString("/")

          artifact
            .copy(
              extra = artifact.extra ++ Map(
                "sources" -> Artifact(srcPath, Map.empty, Map.empty, Attributes("jar", "src")) // Are these the right attributes?
                  .withDefaultChecksums
                  .withDefaultSignature,
                "javadoc" -> Artifact(javadocPath, Map.empty, Map.empty, Attributes("jar", "javadoc")) // Same comment as above
                  .withDefaultChecksums
                  .withDefaultSignature
            ))
        } else
          artifact
            .withJavadocSources
    }

    Seq(artifact)
  }
}

object BaseMavenRepository {

  def ivyLikePath(
    org: String,
    name: String,
    version: String,
    subDir: String,
    baseSuffix: String,
    ext: String
  ) =
    Seq(
      org,
      name,
      version,
      subDir,
      s"$name$baseSuffix.$ext"
    )

  def mavenVersioning(
    snapshotVersioning: SnapshotVersioning,
    classifier: String,
    extension: String
  ): Option[String] =
    snapshotVersioning
      .snapshotVersions
      .find(v => v.classifier == classifier && v.extension == extension)
      .map(_.value)
      .filter(_.nonEmpty)

}

abstract class BaseMavenRepository(
  root: String,
  ivyLike: Boolean
) extends Repository {

  def fetch(
    artifact: Artifact,
    cachePolicy: CachePolicy
  ): EitherT[Task, String, String]

  import Repository._
  import BaseMavenRepository._

  val source = MavenSource(root, ivyLike)

  def projectArtifact(
    module: Module,
    version: String,
    versioningValue: Option[String]
  ): Artifact = {

    val path = (
      if (ivyLike)
        ivyLikePath(
          module.organization,
          module.name,
          versioningValue getOrElse version,
          "poms",
          "",
          "pom"
        )
      else
        module.organization.split('.').toSeq ++ Seq(
          module.name,
          version,
          s"${module.name}-${versioningValue getOrElse version}.pom"
        )
    ) .map(encodeURIComponent)

    Artifact(
      path.mkString("/"),
      Map.empty,
      Map.empty,
      Attributes("pom", "")
    )
    .withDefaultSignature
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
          Map.empty,
          Attributes("pom", "")
        )
        .withDefaultChecksums

      Some(artifact)
    }

  def snapshotVersioningArtifact(
    module: Module,
    version: String
  ): Option[Artifact] =
    if (ivyLike) None
    else {
      val path = (
        module.organization.split('.').toSeq ++ Seq(
          module.name,
          version,
          "maven-metadata.xml"
        )
      ) .map(encodeURIComponent)

      val artifact =
        Artifact(
          path.mkString("/"),
          Map.empty,
          Map.empty,
          Attributes("pom", "")
        )
        .withDefaultChecksums

      Some(artifact)
    }

  def versions(
    module: Module,
    cachePolicy: CachePolicy = CachePolicy.Default
  ): EitherT[Task, String, Versions] = {

    EitherT(
      versionsArtifact(module) match {
        case None => Task.now(-\/("Not supported"))
        case Some(artifact) =>
          fetch(artifact, cachePolicy)
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

  def snapshotVersioning(
    module: Module,
    version: String,
    cachePolicy: CachePolicy = CachePolicy.Default
  ): EitherT[Task, String, SnapshotVersioning] = {

    EitherT(
      snapshotVersioningArtifact(module, version) match {
        case None => Task.now(-\/("Not supported"))
        case Some(artifact) =>
          fetch(artifact, cachePolicy)
            .run
            .map(eitherStr =>
              for {
                str <- eitherStr
                xml <- \/.fromEither(compatibility.xmlParse(str))
                _ <- if (xml.label == "metadata") \/-(()) else -\/("Metadata not found")
                snapshotVersioning <- Xml.snapshotVersioning(xml)
              } yield snapshotVersioning
            )
      }
    )
  }

  def findNoInterval(
    module: Module,
    version: String,
    cachePolicy: CachePolicy
  ): EitherT[Task, String, Project] =
    EitherT{
      def withSnapshotVersioning =
        snapshotVersioning(module, version, cachePolicy)
          .flatMap { snapshotVersioning =>
            val versioningOption =
              mavenVersioning(snapshotVersioning, "", "jar")
                .orElse(mavenVersioning(snapshotVersioning, "", ""))

            versioningOption match {
              case None =>
                EitherT[Task, String, Project](
                  Task.now(-\/("No snapshot versioning value found"))
                )
              case versioning @ Some(_) =>
                findVersioning(module, version, versioning, cachePolicy)
                  .map(_.copy(snapshotVersioning = Some(snapshotVersioning)))
            }
          }

      findVersioning(module, version, None, cachePolicy)
        .run
        .flatMap{ eitherProj =>
          if (eitherProj.isLeft)
            withSnapshotVersioning
              .run
              .map(eitherProj0 =>
                if (eitherProj0.isLeft)
                  eitherProj
                else
                  eitherProj0
              )
          else
            Task.now(eitherProj)
        }
    }

  def findVersioning(
    module: Module,
    version: String,
    versioningValue: Option[String],
    cachePolicy: CachePolicy
  ): EitherT[Task, String, Project] = {

    EitherT {
      fetch(projectArtifact(module, version, versioningValue), cachePolicy)
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

  def find(
    module: Module,
    version: String
  )(implicit
    cachePolicy: CachePolicy
  ): EitherT[Task, String, (Artifact.Source, Project)] = {

    Parse.versionInterval(version)
      .filter(_.isValid) match {
        case None =>
          findNoInterval(module, version, cachePolicy).map((source, _))
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
                case -\/(reason) => EitherT[Task, String, (Artifact.Source, Project)](Task.now(-\/(reason)))
                case \/-(version0) =>
                  findNoInterval(module, version0, cachePolicy)
                    .map(_.copy(versions = Some(versions0)))
                    .map((source, _))
              }
            }
    }
  }

}

sealed trait CachePolicy {
  def apply[E,T](local: => Task[E \/ T])
                (remote: => Task[E \/ T]): Task[E \/ T]

  def saving[E,T](local: => Task[E \/ T])
                 (remote: => Task[E \/ T])
                 (save: => T => Task[Unit]): Task[E \/ T] =
    apply(local)(CachePolicy.saving(remote)(save))
}

object CachePolicy {
  def saving[E,T](remote: => Task[E \/ T])
                 (save: T => Task[Unit]): Task[E \/ T] = {
    for {
      res <- remote
      _ <- res.fold(_ => Task.now(()), t => save(t))
    } yield res
  }

  case object Default extends CachePolicy {
    def apply[E,T](local: => Task[E \/ T])
                  (remote: => Task[E \/ T]): Task[E \/ T] =
      local
        .flatMap(res => if (res.isLeft) remote else Task.now(res))
  }
  case object LocalOnly extends CachePolicy {
    def apply[E,T](local: => Task[E \/ T])
                  (remote: => Task[E \/ T]): Task[E \/ T] =
      local
  }
  case object ForceDownload extends CachePolicy {
    def apply[E,T](local: => Task[E \/ T])
                  (remote: => Task[E \/ T]): Task[E \/ T] =
      remote
  }
}
