package coursier.core

import coursier.Fetch

import scala.language.higherKinds
import scalaz.Monad
import coursier.core.compatibility.encodeURIComponent
import coursier.util.EitherT

trait Repository extends Product with Serializable {
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
        "SHA-1" -> (underlying.url + ".sha1"),
        "SHA-256" -> (underlying.url + ".sha256")
      ))
    def withDefaultSignature: Artifact =
      underlying.copy(extra = underlying.extra ++ Seq(
        "sig" ->
          Artifact(
            underlying.url + ".asc",
            Map.empty,
            Map.empty,
            Attributes("asc", ""),
            changing = underlying.changing,
            authentication = underlying.authentication
          )
            .withDefaultChecksums
      ))
  }
}

