package sbt.internal

import java.lang.{ ProcessBuilder => JProcessBuilder }

private[sbt] object InheritInput {
  def apply(p: JProcessBuilder): Boolean = (redirectInput, inherit) match {
    case (Some(m), Some(f)) =>
      m.invoke(p, f); true
    case _ => false
  }

  private val pbClass = Class.forName("java.lang.ProcessBuilder")
  private val redirectClass = pbClass.getClasses find (_.getSimpleName == "Redirect")

  private val redirectInput = redirectClass map (pbClass.getMethod("redirectInput", _))
  private val inherit = redirectClass map (_ getField "INHERIT" get null)
}
