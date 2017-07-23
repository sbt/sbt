package coursier
package core

import scala.annotation.tailrec
import scala.language.higherKinds
import scalaz.{-\/, Monad, \/, \/-}
import scalaz.Scalaz.{ToFunctorOps, ToBindOps, ToTraverseOps, vectorInstance}


sealed abstract class ResolutionProcess {
  def run[F[_]](
    fetch: Fetch.Metadata[F],
    maxIterations: Int = ResolutionProcess.defaultMaxIterations
  )(implicit
    F: Monad[F]
  ): F[Resolution] =
    if (maxIterations == 0) F.point(current)
    else {
      val maxIterations0 =
        if (maxIterations > 0) maxIterations - 1 else maxIterations

      this match {
        case Done(res) =>
          F.point(res)
        case missing0 @ Missing(missing, _, _) =>
          F.bind(ResolutionProcess.fetchAll(missing, fetch))(result =>
            missing0.next(result).run(fetch, maxIterations0)
          )
        case cont @ Continue(_, _) =>
          cont
            .nextNoCont
            .run(fetch, maxIterations0)
      }
    }

  @tailrec
  final def next[F[_]](
    fetch: Fetch.Metadata[F],
    fastForward: Boolean = true
  )(implicit
    F: Monad[F]
  ): F[ResolutionProcess] =
    this match {
      case Done(_) =>
        F.point(this)
      case missing0 @ Missing(missing, _, _) =>
        F.map(ResolutionProcess.fetchAll(missing, fetch))(result => missing0.next(result))
      case cont @ Continue(_, _) =>
        if (fastForward)
          cont.nextNoCont.next(fetch, fastForward = fastForward)
        else
          F.point(cont.next)
    }

  def current: Resolution
}

final case class Missing(
  missing: Seq[(Module, String)],
  current: Resolution,
  cont: Resolution => ResolutionProcess
) extends ResolutionProcess {

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
              case (_, v) => v.size == min
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
          case (acc, (modVer0, (source, proj))) =>
            acc.copyWithCache(
              projectCache = acc.projectCache + (
                modVer0 -> (source, acc.withDependencyManagement(proj))
              )
            )
        }

        Continue(res0, cont)
      } else
        Missing(depMgmtMissing.toSeq, res, cont0)
    }

    val current0 = current.copyWithCache(
      errorCache = current.errorCache ++ errors
    )

    cont0(current0)
  }

  @deprecated("Intended for internal use only", "1.0.0-RC7")
  def uniqueModules: Missing =
    this

}

final case class Continue(
  current: Resolution,
  cont: Resolution => ResolutionProcess
) extends ResolutionProcess {

  def next: ResolutionProcess = cont(current)

  @tailrec def nextNoCont: ResolutionProcess =
    next match {
      case nextCont: Continue => nextCont.nextNoCont
      case other => other
    }

}

final case class Done(resolution: Resolution) extends ResolutionProcess {
  def current: Resolution = resolution
}

object ResolutionProcess {

  def defaultMaxIterations: Int = 100

  def apply(resolution: Resolution): ResolutionProcess = {
    val resolution0 = resolution.nextIfNoMissing

    if (resolution0.isDone)
      Done(resolution0)
    else
      Missing(resolution0.missingFromCache.toSeq, resolution0, apply)
  }

  private[coursier] def fetchAll[F[_]](
    modVers: Seq[(Module, String)],
    fetch: Fetch.Metadata[F]
  )(implicit F: Monad[F]): F[Vector[((Module, String), Seq[String] \/ (Artifact.Source, Project))]] = {

    def uniqueModules(modVers: Seq[(Module, String)]): Stream[Seq[(Module, String)]] = {

      val res = modVers.groupBy(_._1).toSeq.map(_._2).map {
        case Seq(v) => (v, Nil)
        case Seq() => sys.error("Cannot happen")
        case v =>
          // there might be version intervals in there, but that shouldn't matter...
          val res = v.maxBy { case (_, v0) => Version(v0) }
          (res, v.filter(_ != res))
      }

      val other = res.flatMap(_._2)

      if (other.isEmpty)
        Stream(modVers)
      else {
        val missing0 = res.map(_._1)
        missing0 #:: uniqueModules(other)
      }
    }

    uniqueModules(modVers)
      .toVector
      .foldLeft(F.point(Vector.empty[((Module, String), Seq[String] \/ (Artifact.Source, Project))])) {
        (acc, l) =>
          for (v <- acc; e <- fetch(l))
            yield v ++ e
      }
  }

}

