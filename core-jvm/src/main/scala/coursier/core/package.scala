package coursier

import scalaz.EitherT
import scalaz.concurrent.Task

package object core {

  def resolution(dependencies: Set[Dependency],
                 fetch: ModuleVersion => EitherT[Task, List[String], (Repository, Project)],
                 filter: Option[Dependency => Boolean],
                 profileActivation: Option[(String, Activation, Map[String, String]) => Boolean]): Stream[Resolution] = {

    val startResolution = Resolution(
      dependencies, dependencies, Set.empty,
      Map.empty, Map.empty,
      filter,
      profileActivation
    )

    def helper(resolution: Resolution): Stream[Resolution] = {
      if (resolution.isDone) Stream()
      else {
        val nextRes = resolution.next(fetch).run
        nextRes #:: helper(nextRes)
      }
    }

    startResolution #:: helper(startResolution)
  }

}
