package coursier.core

import scala.language.higherKinds

import scalaz._

import coursier.core.compatibility.encodeURIComponent

trait Repository {
  def find[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)]
}

object Repository {

  type Fetch[F[_]] = Artifact => EitherT[F, String, String]

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
  def find[F[_]](
    repositories: Seq[Repository],
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, Seq[String], (Artifact.Source, Project)] = {

    val lookups = repositories
      .map(repo => repo -> repo.find(module, version, fetch).run)

    val task = lookups.foldLeft[F[Seq[String] \/ (Artifact.Source, Project)]](F.point(-\/(Nil))) {
      case (acc, (repo, eitherProjTask)) =>
        F.bind(acc) {
          case -\/(errors) =>
            F.map(eitherProjTask)(_.flatMap{case (source, project) =>
              if (project.module == module) \/-((source, project))
              else -\/(s"Wrong module returned (expected: $module, got: ${project.module})")
            }.leftMap(error => error +: errors))

          case res @ \/-(_) =>
            F.point(res)
        }
    }

    EitherT(F.map(task)(_.leftMap(_.reverse)))
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

