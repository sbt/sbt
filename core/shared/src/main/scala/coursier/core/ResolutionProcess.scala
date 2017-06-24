package coursier
package core

import scalaz._
import scala.annotation.tailrec
import scala.language.higherKinds


sealed abstract class ResolutionProcess {
  def run[F[_]](
    fetch: Fetch.Metadata[F],
    maxIterations: Int = 50
  )(implicit
    F: Monad[F]
  ): F[Resolution] = {

    if (maxIterations == 0) F.point(current)
    else {
      val maxIterations0 =
        if (maxIterations > 0) maxIterations - 1 else maxIterations

      this match {
        case Done(res) =>
          F.point(res)
        case missing0 @ Missing(missing, _, _) =>
          F.bind(fetch(missing))(result =>
            missing0.next(result).run(fetch, maxIterations0)
          )
        case cont @ Continue(_, _) =>
          cont
            .nextNoCont
            .run(fetch, maxIterations0)
      }
    }
  }

  @tailrec
  final def next[F[_]](
    fetch: Fetch.Metadata[F],
    fastForward: Boolean = true
  )(implicit
    F: Monad[F]
  ): F[ResolutionProcess] = {

    this match {
      case Done(res) =>
        F.point(this)
      case missing0 @ Missing(missing, _, _) =>
        F.map(fetch(missing))(result => missing0.next(result))
      case cont @ Continue(_, _) =>
        if (fastForward)
          cont.nextNoCont.next(fetch, fastForward = fastForward)
        else
          F.point(cont.next)
    }
  }

  def current: Resolution
}

final case class Missing(
  missing: Seq[(Module, String)],
  current: Resolution,
  cont: Resolution => ResolutionProcess
) extends ResolutionProcess {

  def uniqueModules: Missing = {

    // only try to fetch a single version of a given module in a same iteration, so that resolutions for different
    // versions don't try to download the same URL in the same iteration, which the coursier.Cache.Logger API doesn't
    // allow

    val missing0 = missing.groupBy(_._1).toSeq.map(_._2).map {
      case Seq(v) => v
      case Seq() => sys.error("Cannot happen")
      case v =>
        v.maxBy { case (_, v0) => Version(v0) }
    }

    copy(missing = missing0)
  }

  def next(results: Fetch.MD): ResolutionProcess = {

    val errors = results.collect {
      case (modVer, -\/(errs)) =>
        modVer -> errs
    }
    val successes = results.collect {
      case (modVer, \/-(repoProj)) =>
        modVer -> repoProj
    }

    def cont0(res: Resolution): ResolutionProcess = {

      val depMgmtMissing0 = successes.map {
        case elem @ (_, (_, proj)) =>
          elem -> res.dependencyManagementMissing(proj)
      }

      val depMgmtMissing = depMgmtMissing0.map(_._2).fold(Set.empty)(_ ++ _) -- results.map(_._1)

      if (depMgmtMissing.isEmpty) {

        type Elem = ((Module, String), (Artifact.Source, Project))
        val modVer = depMgmtMissing0.map(_._1._1).toSet

        @tailrec
        def order(map: Map[Elem, Set[(Module, String)]], acc: List[Elem]): List[Elem] =
          if (map.isEmpty)
            acc.reverse
          else {
            val min = map.map(_._2.size).min // should be 0
            val (toAdd, remaining) = map.partition {
              case (k, v) => v.size == min
            }
            val acc0 = toAdd.keys.foldLeft(acc)(_.::(_))
            val remainingKeys = remaining.keySet.map(_._1)
            val map0 = remaining.map {
              case (k, v) =>
                k -> v.intersect(remainingKeys)
            }
            order(map0, acc0)
          }

        val orderedSuccesses = order(depMgmtMissing0.map { case (k, v) => k -> v.intersect(modVer) }.toMap, Nil)

        val res0 = orderedSuccesses.foldLeft(res) {
          case (acc, (modVer, (source, proj))) =>
            acc.copyWithCache(
              projectCache = acc.projectCache + (
                modVer -> (source, acc.withDependencyManagement(proj))
              )
            )
        }

        Continue(res0, cont)
      } else
        Missing(depMgmtMissing.toSeq, res, cont0).uniqueModules
    }

    val current0 = current.copyWithCache(
      errorCache = current.errorCache ++ errors
    )

    cont0(current0)
  }

}

final case class Continue(
  current: Resolution,
  cont: Resolution => ResolutionProcess
) extends ResolutionProcess {

  def next: ResolutionProcess = cont(current)

  @tailrec final def nextNoCont: ResolutionProcess =
    next match {
      case nextCont: Continue => nextCont.nextNoCont
      case other => other
    }

}

final case class Done(resolution: Resolution) extends ResolutionProcess {
  def current: Resolution = resolution
}

object ResolutionProcess {
  def apply(resolution: Resolution): ResolutionProcess = {
    val resolution0 = resolution.nextIfNoMissing

    if (resolution0.isDone)
      Done(resolution0)
    else
      Missing(resolution0.missingFromCache.toSeq, resolution0, apply).uniqueModules
  }
}

