ThisBuild / turbo := true

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import scala.collection.JavaConverters._
val rewriteIvy = inputKey[Unit]("Rewrite ivy directory")

ThisBuild / useCoursier := false

val snapshot = (project in file(".")).settings(
  name := "akka-test",
  scalaVersion := "2.12.20",
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "utest" % "0.6.6" % "test"
  ),
  testFrameworks += TestFramework("utest.runner.Framework"),
  resolvers += "Local Maven" at file("ivy").toURI.toURL.toString,
  libraryDependencies += "sbt" %% "foo-lib" % "0.1.0-SNAPSHOT",
  rewriteIvy := {
    val dir = Def.spaceDelimited().parsed.head
    sbt.IO.delete(baseDirectory.value / "ivy")
    sbt.IO.copyDirectory(baseDirectory.value / s"libraries/library-$dir/ivy", baseDirectory.value / "ivy")
    Files.walk(file("ivy").getCanonicalFile.toPath).iterator.asScala.foreach { f =>
     Files.setLastModifiedTime(f, FileTime.fromMillis(System.currentTimeMillis + 3000))
    }
  }
)
