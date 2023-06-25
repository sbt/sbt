package sbt.librarymanagement

import collection.mutable
import sbt.util.ShowLines
import sbt.internal.util.{ SourcePosition, LinePosition, RangePosition, LineRange }

final class ResolveException(
    val messages: Seq[String],
    val failed: Seq[ModuleID],
    val failedPaths: Map[ModuleID, Seq[ModuleID]]
) extends RuntimeException(messages.mkString("\n")) {
  def this(messages: Seq[String], failed: Seq[ModuleID]) =
    this(
      messages,
      failed,
      Map(failed map { m =>
        m -> Nil
      }: _*)
    )
}

/**
 * Represents unresolved dependency warning, which displays reconstructed dependency tree
 * along with source position of each node.
 */
final class UnresolvedWarning(
    val resolveException: ResolveException,
    val failedPaths: Seq[Seq[(ModuleID, Option[SourcePosition])]]
)

object UnresolvedWarning {
  def apply(
      err: ResolveException,
      config: UnresolvedWarningConfiguration
  ): UnresolvedWarning = {
    def modulePosition(m0: ModuleID): Option[SourcePosition] =
      config.modulePositions.find { case (m, _) =>
        (m.organization == m0.organization) &&
        (m0.name startsWith m.name) &&
        (m.revision == m0.revision)
      } map { case (_, p) =>
        p
      }
    val failedPaths = err.failed map { (x: ModuleID) =>
      err.failedPaths(x).toList.reverse map { id =>
        (id, modulePosition(id))
      }
    }
    new UnresolvedWarning(err, failedPaths)
  }

  private[sbt] def sourcePosStr(posOpt: Option[SourcePosition]): String =
    posOpt match {
      case Some(LinePosition(path, start))                  => s" ($path#L$start)"
      case Some(RangePosition(path, LineRange(start, end))) => s" ($path#L$start-$end)"
      case _                                                => ""
    }
  implicit val unresolvedWarningLines: ShowLines[UnresolvedWarning] = ShowLines { a =>
    val withExtra = a.resolveException.failed.filter(_.extraDependencyAttributes.nonEmpty)
    val buffer = mutable.ListBuffer[String]()
    if (withExtra.nonEmpty) {
      buffer += "\n\tNote: Some unresolved dependencies have extra attributes.  Check that these dependencies exist with the requested attributes."
      withExtra foreach { id =>
        buffer += "\t\t" + id
      }
    }
    if (a.failedPaths.nonEmpty) {
      buffer += "\n\tNote: Unresolved dependencies path:"
      a.failedPaths foreach { path =>
        if (path.nonEmpty) {
          val head = path.head
          buffer += "\t\t" + head._1.toString + sourcePosStr(head._2)
          path.tail foreach { case (m, pos) =>
            buffer += "\t\t  +- " + m.toString + sourcePosStr(pos)
          }
        }
      }
    }
    buffer.toList
  }
}

final class UnresolvedWarningConfiguration private[sbt] (
    val modulePositions: Map[ModuleID, SourcePosition]
)
object UnresolvedWarningConfiguration {
  def apply(): UnresolvedWarningConfiguration = apply(Map())
  def apply(modulePositions: Map[ModuleID, SourcePosition]): UnresolvedWarningConfiguration =
    new UnresolvedWarningConfiguration(modulePositions)
}
