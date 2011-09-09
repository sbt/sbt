package name.example

import sbt._

object P extends Plugin {
	val xyz = 3
	val checkMaxErrors = TaskKey[Unit]("check-max-errors")
	val checkName = TaskKey[Unit]("check-name")

	override def settings = Seq[Setting[_]](
		checkMaxErrors <<= Keys.maxErrors map { me => assert(me == xyz, "Expected maxErrors to be " + xyz + ", but it was " + me ) },
		checkName <<= Keys.name map { n => assert(n == "Demo", "Expected name to be 'Demo', but it was '" + n + "'" ) }
	)
}