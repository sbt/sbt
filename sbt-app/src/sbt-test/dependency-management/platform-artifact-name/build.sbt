ThisBuild / organization := "com.example"
name := "testproj"
version := "0.1.0-SNAPSHOT"
scalaVersion := "3.8.3"

platform := "native0.5"
crossVersion := CrossVersion.binary

lazy val checkPomName = taskKey[Unit]("check POM artifact path includes native0.5_3")

checkPomName := {
  val converter = fileConverter.value
  val p = (makePom / artifactPath).value
  val s = converter.toPath(p).toString
  assert(s.contains("native0.5_3"), s"expected native0.5_3 in POM path, got: $s")
}
