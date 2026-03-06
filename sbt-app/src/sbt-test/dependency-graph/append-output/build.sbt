scalaVersion := "2.12.21"
organization := "org.example"
version := "0.1"

lazy val root = (project in file("."))
  .aggregate(a, b)
  .settings(
    publish / skip := true,
  )

lazy val a = (project in file("a")).settings(
  name := "a",
  libraryDependencies += "org.typelevel" %% "cats-core" % "2.10.0",
)

lazy val b = (project in file("b")).settings(
  name := "b",
  libraryDependencies += "com.lihaoyi" %% "pprint" % "0.9.0",
)

TaskKey[Unit]("check") := {
  val f = new File("target/tree.txt")
  val content = IO.read(f)
  assert(content.contains("org.typelevel:cats-core_2.12:2.10.0"), s"Missing cats-core in output:\n$content")
  assert(content.contains("com.lihaoyi:pprint_2.12:0.9.0"), s"Missing pprint in output:\n$content")
}
