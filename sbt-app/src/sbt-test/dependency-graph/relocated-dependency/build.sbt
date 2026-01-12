// #8400
scalaVersion := "2.12.21"
libraryDependencies += "org.lz4" % "lz4-java" % "1.8.1"

TaskKey[Unit]("check") := {
  val content = IO.read(new File("target/tree.txt"))
  assert(
    content.contains("at.yawk.lz4:lz4-java:1.8.1"),
    s"Expected relocated dependency in tree:\n$content"
  )
}
