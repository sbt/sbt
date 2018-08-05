import sjsonnew.BasicJsonProtocol._

lazy val x = taskKey[Int]("x")
lazy val y = taskKey[Int]("y")
lazy val checkScopes = inputKey[Unit]("check scopes")

lazy val subA = project
lazy val subB = project

x := 3

x in Compile in y := 7

x in Runtime in y := 13

x in subA in Compile := {
	val xcy = (x in Compile in y).previous getOrElse 0  // 7
	// verify that This is properly resolved to Global and not the defining key's scope
	val xg = x.previous getOrElse 0  // 3
	println(s"xcy=$xcy, xg=$xg")
	xcy * xg
}


inConfig(Compile)(Seq(
	y in subB := {
		// verify that the referenced key gets delegated
		val xty = (x in Test in y).previous getOrElse 0  // 13
		// verify that inConfig gets applied
		val xcy = (x in y).previous getOrElse 0  // 7
		println(s"xcy=$xcy, xty=$xty")
		xty * xcy
	}
))

def parser = {
	import complete.DefaultParsers._
	(Space ~> IntBasic) ~ (Space ~> IntBasic)
}

checkScopes := {
	val (expectedX, expectedY) = parser.parsed
	val actualX = (x in subA in Compile).value
	val actualY = (y in subB in Test).value
	assert(actualX == expectedX, s"Expected 'x' to be $expectedX, got $actualX")
	assert(actualY == expectedY, s"Expected 'y' to be $expectedY, got $actualY")
}
