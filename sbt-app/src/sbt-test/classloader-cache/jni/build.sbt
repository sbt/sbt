import java.nio.file._
import scala.collection.JavaConverters._

val copyTestResources = inputKey[Unit]("Copy the native libraries to the base directory")
val appendToLibraryPath = taskKey[Unit]("Append the base directory to the java.library.path system property")
val dropLibraryPath = taskKey[Unit]("Drop the last path from the java.library.path system property")
val wrappedRun = taskKey[Unit]("Run with modified java.library.path")
val wrappedTest = taskKey[Unit]("Test with modified java.library.path")

def wrap(task: InputKey[Unit]): Def.Initialize[Task[Unit]] =
  Def.sequential(appendToLibraryPath, task.toTask(""), dropLibraryPath)

ThisBuild / turbo := true

val root = (project in file(".")).settings(
  scalaVersion := "2.12.20",
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-h",
  sourceDirectory.value.toPath.resolve("main/native/include").toString),
  libraryDependencies += "com.lihaoyi" %% "utest" % "0.6.6" % "test",
  testFrameworks := Seq(new TestFramework("utest.runner.Framework")),
  copyTestResources := {
    val key = Def.spaceDelimited().parsed.head
    val base = baseDirectory.value.toPath
    val resources = (baseDirectory.value / "src" / "main" / "resources" / key).toPath
    Files.walk(resources).iterator.asScala.foreach { p =>
      Files.copy(p, base.resolve(p.getFileName), StandardCopyOption.REPLACE_EXISTING)
    }
  },
  appendToLibraryPath := {
    val cp = System.getProperty("java.library.path", "").split(":")
    val newCp = if (cp.contains(".")) cp else cp :+ "."
    System.setProperty("java.library.path", newCp.mkString(":"))
  },
  dropLibraryPath := {
    val cp = System.getProperty("java.library.path", "").split(":").dropRight(1)
    System.setProperty("java.library.path", cp.mkString(":"))
  },
  wrappedRun := wrap(Runtime / run).value,
  wrappedTest := wrap(Test / testOnly).value
)
