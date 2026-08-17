import java.nio.file.Files
import java.nio.file.attribute.FileTime

import sbtassembly.AssemblyPlugin.autoImport._

sbtPlugin := true

val expectedMtime = FileTime.fromMillis(1234L)
val setAssemblyMtime = taskKey[Unit]("Sets the assembly output mtime")
val checkAssemblyMtime = taskKey[Unit]("Checks that assembly did not rewrite its output")
val checkAssemblyRebuilt = taskKey[Unit]("Checks that assembly rebuilt its output")

setAssemblyMtime := {
  val artifact = (assembly / assemblyOutputPath).value
  Files.setLastModifiedTime(artifact.toPath, expectedMtime)
}

checkAssemblyMtime := {
  val artifact = (assembly / assemblyOutputPath).value
  val actual = Files.getLastModifiedTime(artifact.toPath)
  assert(actual == expectedMtime, s"assembly rewrote $artifact: $actual")
}

checkAssemblyRebuilt := {
  val artifact = (assembly / assemblyOutputPath).value
  val actual = Files.getLastModifiedTime(artifact.toPath)
  assert(actual != expectedMtime, s"assembly did not rebuild $artifact")
}
