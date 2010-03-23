/* sbt -- Simple Build Tool
 * Copyright 2010 Tony Sloane
 */
package sbt

object StackTrace
{
	def isSbtClass(name: String) = name.startsWith("sbt") || name.startsWith("xsbt")
	/**
		* Return a printable representation of the stack trace associated
		* with t.  Information about t and its Throwable causes is included.
		* The number of lines to be included for each Throwable is configured
		* via d which should be greater than or equal to zero.  If d is zero,
		* then all elements are included up to (but not including) the first
		* element that comes from sbt. If d is greater than zero, then up to
		* that many lines are included, where the line for the Throwable is
		* counted plus one line for each stack element.  Less lines will be
		* included if there are not enough stack elements.
		*/
	def trimmed(t : Throwable, d : Int) : String = {
		require(d >= 0)
		val b = new StringBuilder ()

		def appendStackTrace (t : Throwable, first : Boolean) {

			val include : StackTraceElement => Boolean =
				if (d == 0)
					element => !isSbtClass(element.getClassName)
				else {
					var count = d - 1
					(_ => { count -= 1; count >= 0 })
				}

			def appendElement (e : StackTraceElement) {
				b.append ("\tat ")
				b.append (e)
				b.append ('\n')
			}

			if (!first)
				b.append ("Caused by: ")
			b.append (t)
			b.append ('\n')

			val els = t.getStackTrace ()
			var i = 0
			while ((i < els.size) && include (els (i))) {
				appendElement (els (i))
				i += 1
			}

		}

		appendStackTrace (t, true)
		var c = t
		while (c.getCause () != null) {
			c = c.getCause ()
			appendStackTrace (c, false)
		}
		b.toString ()

	}
}