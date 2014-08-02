package sbt

import java.io.File
import collection.mutable
import Def.Setting
import Scope.GlobalScope

private[sbt] final class UnresolvedDependencyWarning(
  val withExtra: Seq[ModuleID],
  val failedPaths: Seq[Seq[(ModuleID, Option[String])]])

private[sbt] object UnresolvedDependencyWarning {
  // This method used to live under IvyActions.scala, but it's moved here because it's now aware of State.
  private[sbt] def apply(err: ResolveException, stateOpt: Option[State]): UnresolvedDependencyWarning = {
    // This is a mapping between modules and original position, in which the module was introduced.
    lazy val modulePositions: Seq[(ModuleID, SourcePosition)] =
      try {
        stateOpt map { state =>
          val extracted = (Project extract state)
          val sk = (Keys.libraryDependencies in (GlobalScope in extracted.currentRef)).scopedKey
          val empty = extracted.structure.data set (sk.scope, sk.key, Nil)
          val settings = extracted.structure.settings filter { s: Setting[_] =>
            (s.key.key == Keys.libraryDependencies.key) &&
              (s.key.scope.project == Select(extracted.currentRef))
          }
          settings flatMap {
            case s: Setting[Seq[ModuleID]] @unchecked =>
              s.init.evaluate(empty) map { _ -> s.pos }
          }
        } getOrElse Seq()
      } catch {
        case _: Throwable => Seq()
      }
    def modulePosition(m0: ModuleID): Option[String] =
      modulePositions.find {
        case (m, p) =>
          (m.organization == m0.organization) &&
            (m0.name startsWith m.name) &&
            (m.revision == m0.revision)
      } flatMap {
        case (m, LinePosition(path, start)) =>
          Some(s" ($path#L$start)")
        case (m, RangePosition(path, LineRange(start, end))) =>
          Some(s" ($path#L$start-$end)")
        case _ => None
      }
    val withExtra = err.failed.filter(!_.extraDependencyAttributes.isEmpty)
    val failedPaths = err.failed map { x: ModuleID =>
      err.failedPaths(x).toList.reverse map { id =>
        (id, modulePosition(id))
      }
    }
    UnresolvedDependencyWarning(withExtra, failedPaths)
  }

  def apply(withExtra: Seq[ModuleID],
    failedPaths: Seq[Seq[(ModuleID, Option[String])]]): UnresolvedDependencyWarning =
    new UnresolvedDependencyWarning(withExtra, failedPaths)

  implicit val unresolvedDependencyWarningLines: ShowLines[UnresolvedDependencyWarning] = ShowLines { a =>
    val buffer = mutable.ListBuffer[String]()
    if (!a.withExtra.isEmpty) {
      buffer += "\n\tNote: Some unresolved dependencies have extra attributes.  Check that these dependencies exist with the requested attributes."
      a.withExtra foreach { id => buffer += "\t\t" + id }
    }
    if (!a.failedPaths.isEmpty) {
      buffer += "\n\tNote: Unresolved dependencies path:"
      a.failedPaths foreach { path =>
        if (!path.isEmpty) {
          val head = path.head
          buffer += "\t\t" + head._1.toString + head._2.getOrElse("")
          path.tail foreach {
            case (m, pos) =>
              buffer += "\t\t  +- " + m.toString + pos.getOrElse("")
          }
        }
      }
    }
    buffer.toList
  }
}
