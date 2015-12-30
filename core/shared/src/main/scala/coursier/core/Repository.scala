package coursier.core

import coursier.Fetch

import scala.language.higherKinds

import scalaz._

import coursier.core.compatibility.encodeURIComponent

trait Repository {
  def find[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)]
}

object Repository {

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

