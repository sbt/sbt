import sbt._, Keys._
import Def.Initialize
import complete.{DefaultParsers, Parser}

object A {
	val x1: Initialize[Parser[Int]] = Def.setting { DefaultParsers.success(3) }
	val y1 = Def.task { x1.parsed }
}
