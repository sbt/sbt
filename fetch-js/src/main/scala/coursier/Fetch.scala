package coursier

import scalaz.concurrent.Task

object Fetch {

  implicit def default(
    repositories: Seq[core.Repository]
  ): ResolutionProcess.Fetch[Task] =
    apply(repositories, Platform.artifact)

  def apply(
    repositories: Seq[core.Repository],
    fetch: Repository.Fetch[Task]
  ): ResolutionProcess.Fetch[Task] = {

    modVers => Task.gatherUnordered(
      modVers.map { case (module, version) =>
        Repository.find(repositories, module, version, fetch)
          .run
          .map((module, version) -> _)
      }
    )
  }

}
