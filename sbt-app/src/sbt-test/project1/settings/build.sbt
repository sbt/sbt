import complete.DefaultParsers._

val check = inputKey[Unit]("Check that the value of maxErrors is as expected")
val parser = Space ~> IntBasic

check := {
	val expected = parser.parsed
	val actual = maxErrors.value
	assert(expected == actual, "Expected " + expected + ", got " + actual)
}

