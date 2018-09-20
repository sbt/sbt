package coursier.core

import coursier.Fetch
import coursier.core.compatibility.encodeURIComponent
import coursier.maven.MavenSource
import coursier.util.{EitherT, Monad}

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
    def withDefaultSignature: Artifact = {

      val underlyingExt =
        if (underlying.attributes.`type`.isEmpty)
          "jar"
        else
          // TODO move MavenSource.typeExtension elsewhere
          MavenSource.typeExtension(underlying.attributes.`type`)

      underlying.copy(extra = underlying.extra ++ Seq(
        "sig" ->
          Artifact(
            underlying.url + ".asc",
            Map.empty,
            Map.empty,
            Attributes(s"$underlyingExt.asc", ""),
            changing = underlying.changing,
            authentication = underlying.authentication
          )
            .withDefaultChecksums
      ))
    }
  }
}

