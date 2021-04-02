import sjsonnew.BasicJsonProtocol._

lazy val a0 = 1
lazy val b0 = 1
lazy val a = taskKey[Int]("a")
lazy val b = taskKey[Int]("b")
lazy val next = taskKey[(Int,Int)]("next")
lazy val checkNext = inputKey[Unit]("check-next")


// These test that there is no cycle when referring to previous values (a -> b.previous, b -> a.previous)
// Also, it is ok for b to refer to b.previous:
//  normally, b's definition could not refer to plain b.value because it would be undefined

a := b.previous.getOrElse(a0)

b := a.previous.getOrElse(a0) + b.previous.getOrElse(b0)

next := (a.value, b.value)

def parser = {
	import complete.DefaultParsers._
	(Space ~> IntBasic) ~ (Space ~> IntBasic)
}

checkNext := {
	val (expectedA, expectedB) = parser.parsed
	val actualA = a.value
	val actualB = b.value
	assert(actualA == expectedA, s"Expected 'a' to be $expectedA, got $actualA")
	assert(actualB == expectedB, s"Expected 'b' to be $expectedB, got $actualB")
}
