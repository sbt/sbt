scalaVersion := "2.12.8"

organization := "io.get-coursier.test"
name := "dependency-overrides"
version := "0.1.0-SNAPSHOT"

libraryDependencies += "io.get-coursier" %% "coursier" % "2.0.0-RC2-6"
dependencyOverrides += "io.get-coursier" %% "coursier-core" % "1.1.0-M14-7"

lazy val check = taskKey[Unit]("")

check := {
  val f = coursierWriteIvyXml.value
  val content = new String(java.nio.file.Files.readAllBytes(f.toPath), "UTF-8")
  System.err.println(s"ivy.xml:\n'$content'")
  assert(content.contains("<override "), s"No override found in '$content'")
}
