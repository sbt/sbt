lazy val helloWithoutStreams = taskKey[Unit]("")
lazy val helloWithStreams = taskKey[Unit]("")
lazy val checkTraceLevel = taskKey[Unit]("")

helloWithoutStreams := {
  throw new RuntimeException("boom without streams!")
}

helloWithStreams := {
  val log = streams.value.log
  throw new RuntimeException("boom with streams!")
}

checkTraceLevel := {
  val level = traceLevel.value
  assert(level != -1, s"Expected traceLevel != -1 in batch mode, but got $level")
}
