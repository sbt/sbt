import sbt._, Keys._
import Def.Initialize
import complete.{DefaultParsers, Parser}

object A {
	val x1: Initialize[Task[Int]] = Def.task { 3 }
	val y1 = Def.task { x1.value }

	val x2: Initialize[Parser[Int]] = Def.setting { DefaultParsers.success(3) }
	val y2 = Def.inputTask { x1.value + x2.parsed }
	
	val x3: Initialize[Int] = Def.setting { 3 }
	val y3 = Def.setting { x3.value }
}
