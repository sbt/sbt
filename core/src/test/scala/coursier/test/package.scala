package coursier

import scalaz.EitherT
import scalaz.concurrent.Task

package object test {

  implicit class DependencyOps(val underlying: Dependency) extends AnyVal {
    def withCompileScope: Dependency = underlying.copy(scope = Scope.Compile)
  }

  def resolve(dependencies: Set[Dependency],
              fetch: ModuleVersion => EitherT[Task, Seq[String], (Repository, Project)],
              maxIterations: Option[Int] = Some(200),
              filter: Option[Dependency => Boolean] = None,
              profileActivation: Option[(String, Profile.Activation, Map[String, String]) => Boolean] = None): Task[Resolution] = {

    val startResolution = Resolution(
      dependencies,
      filter = filter,
      profileActivation = profileActivation
    )

    startResolution.last(fetch, maxIterations.getOrElse(-1))
  }
}
