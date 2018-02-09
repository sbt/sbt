package sbt.internal.util

sealed trait SourcePosition

sealed trait FilePosition extends SourcePosition {
  def path: String
  def startLine: Int
}

case object NoPosition extends SourcePosition

final case class LinePosition(path: String, startLine: Int) extends FilePosition

final case class LineRange(start: Int, end: Int) {
  def shift(n: Int) = new LineRange(start + n, end + n)
}

final case class RangePosition(path: String, range: LineRange) extends FilePosition {
  def startLine = range.start
}

object SourcePosition {

  /** Creates a SourcePosition by using the enclosing position of the invocation of this method.
   * @see [[scala.reflect.macros.Enclosures#enclosingPosition]]
   * @return SourcePosition
   */
  def fromEnclosing(): SourcePosition = macro SourcePositionMacro.fromEnclosingImpl

}

import scala.annotation.tailrec
import scala.reflect.macros.blackbox
import scala.reflect.internal.util.UndefinedPosition

final class SourcePositionMacro(val c: blackbox.Context) {
  import c.universe.{ NoPosition => _, _ }

  def fromEnclosingImpl(): Expr[SourcePosition] = {
    val pos = c.enclosingPosition
    if (!pos.isInstanceOf[UndefinedPosition] && pos.line >= 0 && pos.source != null) {
      val f = pos.source.file
      val name = constant[String](ownerSource(f.path, f.name))
      val line = constant[Int](pos.line)
      reify { LinePosition(name.splice, line.splice) }
    } else
      reify { NoPosition }
  }

  private[this] def ownerSource(path: String, name: String): String = {
    @tailrec def inEmptyPackage(s: Symbol): Boolean =
      s != NoSymbol && (
        s.owner == c.mirror.EmptyPackage
          || s.owner == c.mirror.EmptyPackageClass
          || inEmptyPackage(s.owner)
      )

    c.internal.enclosingOwner match {
      case ec if !ec.isStatic       => name
      case ec if inEmptyPackage(ec) => path
      case ec                       => s"(${ec.fullName}) $name"
    }
  }

  private[this] def constant[T: WeakTypeTag](t: T): Expr[T] = c.Expr[T](Literal(Constant(t)))
}
