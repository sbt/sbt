package coursier
package core

import scalaz._
import scala.annotation.tailrec


sealed trait ResolutionProcess {
  def run[F[_]](fetch: ResolutionProcess.Fetch[F],
                maxIterations: Int = -1)
               (implicit F: Monad[F]): F[Resolution] = {

    if (maxIterations == 0) F.point(current)
    else
      this match {
        case Done(res) => F.point(res)
        case missing0 @ Missing(missing, _, _) =>
          F.bind(fetch(missing))(result => missing0.next(result).run(fetch, if (maxIterations > 0) maxIterations - 1 else maxIterations))
        case cont @ Continue(_, _) => cont.nextNoCont.run(fetch)
      }
  }

  def current: Resolution
}

case class Missing(missing: Seq[(Module, String)],
                   current: Resolution,
                   cont: Resolution => ResolutionProcess) extends ResolutionProcess {

  def next(results: ResolutionProcess.FetchResult): ResolutionProcess = {

    val errors = results.collect{case (modVer, -\/(errs)) => modVer -> errs }
    val successes = results.collect{case (modVer, \/-(repoProj)) => modVer -> repoProj }

    val depMgmtMissing0 = successes
      .map{case (_, (_, proj)) => current.dependencyManagementMissing(proj) }
      .fold(Set.empty)(_ ++ _)

    val depMgmtMissing = depMgmtMissing0 -- results.map(_._1)

    def cont0(res: Resolution) = {
      val res0 = 
        successes.foldLeft(res){case (acc, (modVer, (source, proj))) =>
          acc.copy(projectCache = acc.projectCache + (
            modVer -> (source, acc.withDependencyManagement(proj))
          ))
        }
      Continue(res0, cont)
    }

    val current0 = current
      .copy(errorCache = current.errorCache ++ errors)

    if (depMgmtMissing.isEmpty) cont0(current0)
    else Missing(depMgmtMissing.toSeq, current0, cont0)
  }

}

case class Continue(current: Resolution,
                    cont: Resolution => ResolutionProcess) extends ResolutionProcess {

  def next: ResolutionProcess = cont(current)

  @tailrec final def nextNoCont: ResolutionProcess =
    next match {
      case nextCont: Continue => nextCont.nextNoCont
      case other => other
    }

}

case class Done(resolution: Resolution) extends ResolutionProcess {
  def current: Resolution = resolution
}

object ResolutionProcess {
  def apply(resolution: Resolution): ResolutionProcess = {
    val resolution0 = resolution.nextIfNoMissing

    if (resolution0.isDone) Done(resolution0)
    else Missing(resolution0.missingFromCache.toSeq, resolution0, apply)
  }

  type FetchResult = Seq[((Module, String), Seq[String] \/ (Artifact.Source, Project))]
  type Fetch[F[_]] = Seq[(Module, String)] => F[FetchResult]
}

