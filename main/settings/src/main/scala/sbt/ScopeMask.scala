package sbt

import scala.collection.mutable.ListBuffer

/** Specifies the Scope axes that should be used for an operation.  `true` indicates an axis should be used. */
final case class ScopeMask(project: Boolean = true, config: Boolean = true, task: Boolean = true, extra: Boolean = true) {
  def concatShow(p: String, c: String, t: String, sep: String, x: String): String =
    {
      val lb = new ListBuffer[String]
      if (project && p != "") lb.append(p)
      if (config && c != "") lb.append(c)
      if (task && t != "") lb.append(t)

      val inClause =
        lb.toList match {
          case Nil     => ""
          case List(x) => s" in $x"
          case xs      => xs.mkString(" in (", ", ", ")")
        }
      val sb = new StringBuilder
      sb.append(sep)
      sb.append(inClause)
      if (extra) sb.append(x)
      sb.toString
    }

  private[sbt] def concatShow012Style(p: String, c: String, t: String, sep: String, x: String): String =
    {
      val sb = new StringBuilder
      if (project) sb.append(p)
      if (config) sb.append(c)
      if (task) sb.append(t)
      sb.append(sep)
      if (extra) sb.append(x)
      sb.toString
    }
}
