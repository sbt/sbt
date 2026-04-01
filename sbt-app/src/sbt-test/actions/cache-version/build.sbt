scalaVersion := "3.7.4"

lazy val checkCounter = inputKey[Unit]("assert counter file value")

checkCounter := {
  val expected = complete.DefaultParsers.spaceDelimited("<arg>").parsed.head.toInt
  val f = baseDirectory.value / "target" / "counter.txt"
  val actual = if (f.exists()) IO.read(f).trim.toInt else 0
  assert(actual == expected, s"Expected counter=$expected but got $actual")
}

lazy val bumpCounter = taskKey[Int]("cached task that bumps a counter file on each execution")

bumpCounter := {
  val f = baseDirectory.value / "target" / "counter.txt"
  val n = if (f.exists()) IO.read(f).trim.toInt else 0
  val next = n + 1
  IO.write(f, next.toString)
  next
}
