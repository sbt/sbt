// tests that errors are properly propagated for dependsOn, map, and flatMap

lazy val root = (project in file(".")).
  settings(
    a <<= baseDirectory map (b =>  if ((b / "succeed").exists) () else sys.error("fail")),
    b <<= a.task(at => nop dependsOn(at)),
    c <<= a map { _ => () },
    d <<= a flatMap { _ => task { () } }
  )
lazy val a = taskKey[Unit]("")
lazy val b = taskKey[Unit]("")
lazy val c = taskKey[Unit]("")
lazy val d = taskKey[Unit]("")

lazy val input = (project in file("input")).
  settings(
    f <<= inputTask { _ map { args => if (args(0) == "succeed") () else sys.error("fail") } },
    j := sys.error("j"),
    g <<= f dependsOn(j),
    h <<= f map { _ => IO.touch(file("h")) }
  )
lazy val f = inputKey[Unit]("")
lazy val g = inputKey[Unit]("")
lazy val h = inputKey[Unit]("")
lazy val j = taskKey[Unit]("")
