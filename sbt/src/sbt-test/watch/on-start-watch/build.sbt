val checkCount = inputKey[Unit]("check that compile has run a specified number of times")

checkCount := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  assert(Count.get == expected)
}

Compile / compile := {
  Count.increment()
  // Trigger a new build by updating the last modified time
  ((Compile / scalaSource).value / "A.scala").setLastModified(5000)
  (Compile / compile).value
}
