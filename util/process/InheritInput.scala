/* sbt -- Simple Build Tool
 * Copyright 2012 Eugene Vigdorchik
 */
package sbt

import java.lang.{ProcessBuilder => JProcessBuilder}

/** On java 7, inherit input on ProcessBuilder.
 * This doesn't work on Windows, where jline steals the input. */
private[sbt] object InheritInput {
	def apply(p: JProcessBuilder): (Boolean, JProcessBuilder) = (redirectInput, inherit) match {
		case (Some(m), Some(f)) if !isWindows => (true, m.invoke(p, f).asInstanceOf[JProcessBuilder])
		case _ => (false, p)
	}

	private[this] val isWindows = System.getProperty("os.name").toLowerCase.indexOf("windows") >= 0

	private[this]	val pbClass = Class.forName("java.lang.ProcessBuilder")
	private[this] val redirectClass = pbClass.getClasses find (_.getSimpleName == "Redirect")

	private[this] val redirectInput = redirectClass map (pbClass.getMethod("redirectInput", _))
	private[this] val inherit = redirectClass map (_ getField "INHERIT" get null)
}
