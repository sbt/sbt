/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

// The following code is based on scala.tools.nsc.reporters.{AbstractReporter, ConsoleReporter, Reporter}
// Copyright 2002-2009 LAMP/EPFL
// see licenses/LICENSE_Scala
// Original author: Martin Odersky

	import xsbti.{Maybe,Position,Problem,Reporter,Severity}
	import java.util.EnumMap
	import scala.collection.mutable
	import LoggerReporter._
	import Severity.{Error,Info,Warn}

object LoggerReporter
{
	def m2o[S](m: Maybe[S]): Option[S] = if(m.isDefined) Some(m.get) else None

	final class PositionKey(pos: Position)
	{
		def offset = pos.offset
		def sourceFile = pos.sourceFile

		override def equals(o: Any) =
			o match { case pk: PositionKey => equalsKey(pk); case _ => false }

		def equalsKey(o: PositionKey) =
			m2o(pos.offset) == m2o(o.offset) &&
			m2o(pos.sourceFile) == m2o(o.sourceFile)
		override def hashCode =
			m2o(pos.offset).hashCode * 31
			m2o(pos.sourceFile).hashCode
	}

	def countElementsAsString(n: Int, elements: String): String =
		n match {
			case 0 => "no "    + elements + "s"
 			case 1 => "one "   + elements
			case 2 => "two "   + elements + "s"
			case 3 => "three " + elements + "s"
			case 4 => "four "  + elements + "s"
			case _ => "" + n + " " + elements + "s"
		}
}
	
class LoggerReporter(maximumErrors: Int, log: Logger) extends xsbti.Reporter
{
	val positions = new mutable.HashMap[PositionKey, Severity]
	val count = new EnumMap[Severity, Int](classOf[Severity])
	private val allProblems = new mutable.ListBuffer[Problem]

	reset()
	
	def reset()
	{
		count.put(Warn, 0)
		count.put(Info, 0)
		count.put(Error, 0)
		positions.clear()
		allProblems.clear()
	}
	def hasWarnings = count.get(Warn) > 0
	def hasErrors = count.get(Error) > 0
	def problems = allProblems.toArray

	def printSummary()
	{
		val warnings = count.get(Severity.Warn)
		if(warnings > 0)
			log.warn(countElementsAsString(warnings, "warning") + " found")
		val errors = count.get(Severity.Error)
		if(errors > 0)
			log.error(countElementsAsString(errors, "error") + " found")
	}

	def inc(sev: Severity) = count.put(sev, count.get(sev) + 1)

	def display(pos: Position, msg: String, severity: Severity)
	{
		inc(severity)
		if(severity != Warn || maximumErrors <= 0 || count.get(severity) <= maximumErrors)
			print(severityLogger(severity), pos, msg)
	}
	def severityLogger(severity: Severity): (=> String) => Unit =
		m =>
		{
			(severity match
			{
				case Error => log.error(m)
				case Warn => log.warn(m)
				case Info => log.info(m)
			})
		}

	def print(log: (=> String) => Unit, pos: Position, msg: String)
	{
		if(pos.sourcePath.isEmpty && pos.line.isEmpty)
			log(msg)
		else
		{
			val sourcePrefix = m2o(pos.sourcePath).getOrElse("")
			val lineNumberString = m2o(pos.line).map(":" + _ + ":").getOrElse(":") + " "
			log(sourcePrefix + lineNumberString + msg)
			val lineContent = pos.lineContent
			if(!lineContent.isEmpty)
			{
				log(lineContent)
				for(space <- m2o(pos.pointerSpace))
					log(space + "^") // pointer to the column position of the error/warning
			}
		}
	}
	
	def log(pos: Position, msg: String, severity: Severity): Unit =
	{
		allProblems += problem(pos, msg, severity)
		severity match
		{
			case Warn | Error =>
			{
				if(!testAndLog(pos, severity))
					display(pos, msg, severity)
			}
			case _ => display(pos, msg, severity)
		}
	}
	def problem(pos: Position, msg: String, sev: Severity): Problem =
		new Problem
		{
			val position = pos
			val message = msg
			val severity = sev
		}

	def testAndLog(pos: Position, severity: Severity): Boolean =
	{
		if(pos.offset.isEmpty || pos.sourceFile.isEmpty)
			false
		else
		{
			val key = new PositionKey(pos)
			if(positions.get(key).map(_.ordinal >= severity.ordinal).getOrElse(false))
				true
			else
			{
				positions(key) = severity
				false
			}
		}
	}
}