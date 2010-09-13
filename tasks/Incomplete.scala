/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Incomplete.{Error, Value => IValue}
final case class Incomplete(tpe: IValue = Error, message: Option[String] = None, causes: Seq[Incomplete] = Nil, directCause: Option[Throwable] = None)
	extends Exception(message.orNull, directCause.orNull)

object Incomplete extends Enumeration {
	val Skipped, Error = Value
	def show(i: Incomplete, traces: Boolean): String =
	{
		val exceptions = allExceptions(i)
		val traces = exceptions.map(ex => ex.getStackTrace.mkString(ex.toString + "\n\t", "\n\t", "\n"))
		val causeStr = if(i.causes.isEmpty) "" else (i.causes.length + " cause(s)")
		"Incomplete (" + show(i.tpe) + ") " + i.message.getOrElse("") + causeStr + "\n" + traces
	}
	def allExceptions(i: Incomplete): Iterable[Throwable] =
	{
		val exceptions = IDSet.create[Throwable]
		val visited = IDSet.create[Incomplete]
		def visit(inc: Incomplete): Unit =
			visited.process(inc)( () ) {
				exceptions ++= inc.directCause.toList
				inc.causes.foreach(visit)
			}
		visit(i)
		exceptions.all
	}
	def show(tpe: Value) = tpe match { case Skipped=> "skipped"; case Error => "error" }
}