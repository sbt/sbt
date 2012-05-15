/* sbt -- Simple Build Tool
 * Copyright 2012 Eugene Vigdorchik
 */
package sbt

import java.lang.{ProcessBuilder => JProcessBuilder}

/** On java 7, inherit System.in for a ProcessBuilder. */
private[sbt] object InheritInput {
	def apply(p: JProcessBuilder): (Boolean, JProcessBuilder) = (redirectInput, inherit) match {
		case (Some(m), Some(f)) => (true, m.invoke(p, f).asInstanceOf[JProcessBuilder])
		case _ => (false, p)
	}

	private[this] val pbClass = Class.forName("java.lang.ProcessBuilder")
	private[this] val redirectClass = pbClass.getClasses find (_.getSimpleName == "Redirect")

	private[this] val redirectInput = redirectClass map (pbClass.getMethod("redirectInput", _))
	private[this] val inherit = redirectClass map (_ getField "INHERIT" get null)
}
