lazy val helloWithoutStreams = taskKey[Unit]("")
lazy val helloWithStreams = taskKey[Unit]("")

helloWithoutStreams := {
  throw new RuntimeException("boom without streams!")
}

helloWithStreams := {
  val log = streams.value.log
  throw new RuntimeException("boom with streams!")
}
