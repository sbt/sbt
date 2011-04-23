/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import Load.BuildStructure
	import Project.{Initialize, ScopedKey}
	import Keys.{resolvedScoped, streams, TaskStreams}
	import std.TaskExtra._
	import Types.{:+:, idFun}

	import sbinary.{Format, JavaIO, Operations}
	import JavaIO._

object TaskData
{
	val DefaultDataID = "data"

	def apply[I,O](readFrom: Scoped, id: String = DefaultDataID)(f: (State, I) => O)(default: => I)(implicit fmt: Format[I]): Initialize[State => O] =
		resolvedScoped { resolved =>
			s => f(s, readData(Project structure s, resolved, readFrom.key, id) getOrElse default)
		}
	
	def readData[T](structure: BuildStructure, reader: ScopedKey[_], readFrom: AttributeKey[_], id: String)(implicit f: Format[T]): Option[T] =
		try {
			dataStreams(structure, reader, readFrom) { (ts,key) =>
				Operations.read( ts.readBinary(key, id) )(f)
			}
		} catch { case e: Exception => None }

	def dataStreams[T](structure: BuildStructure, reader: ScopedKey[_], readFrom: AttributeKey[_])(f: (TaskStreams, ScopedKey[_]) => T): Option[T] =
		structure.data.definingScope(reader.scope, readFrom) map { defined =>
			val key = ScopedKey(Scope.fillTaskAxis(defined, readFrom), readFrom)
			structure.streams.use(reader)(ts => f(ts, key))
		}
	def write[T, S](i: Initialize[Task[T]], convert: T => S = idFun, id: String = DefaultDataID)(implicit f: Format[S]): Initialize[Task[T]] =
		(streams.identity zipWith i) { (sTask, iTask) =>
			(sTask,iTask) map { case s :+: value :+: HNil =>
				Operations.write( s.binary(id), convert(value) )(f)
				value
			}
		}
}
