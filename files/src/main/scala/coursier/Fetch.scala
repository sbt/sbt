package coursier

import scalaz.concurrent.Task

object Fetch {

  implicit def default(
    repositories: Seq[core.Repository]
  ): ResolutionProcess.Fetch[Task] =
    apply(repositories, Platform.artifact)

  def apply(
    repositories: Seq[core.Repository],
    fetch: Repository.Fetch[Task],
    extra: Repository.Fetch[Task]*
  ): ResolutionProcess.Fetch[Task] = {

    modVers => Task.gatherUnordered(
      modVers.map { case (module, version) =>
        def get(fetch: Repository.Fetch[Task]) =
          Repository.find(repositories, module, version, fetch)
        (get(fetch) /: extra)(_ orElse get(_))
          .run
          .map((module, version) -> _)
      }
    )
  }

}
