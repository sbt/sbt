package coursier

import coursier.util.{EitherT, Monad}

import scala.language.higherKinds
import scalaz.Nondeterminism

object Fetch {

  type Content[F[_]] = Artifact => EitherT[F, String, String]


  type MD = Seq[(
    (Module, String),
    Either[Seq[String], (Artifact.Source, Project)]
  )]

  type Metadata[F[_]] = Seq[(Module, String)] => F[MD]

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
    fetch: Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, Seq[String], (Artifact.Source, Project)] = {

    val lookups = repositories
      .map(repo => repo -> repo.find(module, version, fetch).run)

    val task0 = lookups.foldLeft[F[Either[Seq[String], (Artifact.Source, Project)]]](F.point(Left(Nil))) {
      case (acc, (_, eitherProjTask)) =>
        F.bind(acc) {
          case Left(errors) =>
            F.map(eitherProjTask)(_.left.map(error => error +: errors))
          case res @ Right(_) =>
            F.point(res)
        }
    }

    val task = F.map(task0)(e => e.left.map(_.reverse): Either[Seq[String], (Artifact.Source, Project)])
    EitherT(task)
  }

  def from[F[_]](
    repositories: Seq[core.Repository],
    fetch: Content[F],
    extra: Content[F]*
  )(implicit
    F: Nondeterminism[F]
  ): Metadata[F] = {

    modVers =>
      F.map(
        F.gatherUnordered {
          modVers.map {
            case (module, version) =>
              def get(fetch: Content[F]) = find(repositories, module, version, fetch)
              F.map((get(fetch) /: extra)(_ orElse get(_)).run)(d => (module, version) -> d)
          }
        }
      )(_.toSeq)
  }

}