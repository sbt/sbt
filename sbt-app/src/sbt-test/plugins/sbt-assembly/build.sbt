import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime

import sbtassembly.AssemblyPlugin.autoImport._

val recordDescriptorMtime = taskKey[Unit]("Records the generated plugin descriptor mtime")
val checkDescriptorMtime = taskKey[Unit]("Checks that assembly did not rewrite the generated plugin descriptor")
val checkDescriptorRebuilt = taskKey[Unit]("Checks that assembly rewrote the generated plugin descriptor")
val expectedAssemblyMtime = FileTime.fromMillis(1234L)
val setAssemblyMtime = taskKey[Unit]("Sets the assembly output mtime")
val checkAssemblyMtime = taskKey[Unit]("Checks that assembly did not rewrite its output")
val checkAssemblyRebuilt = taskKey[Unit]("Checks that assembly rebuilt its output")

sbtPlugin := true

recordDescriptorMtime := Def.uncached {
  val descriptor = (Compile / resourceManaged).value.toPath.resolve("sbt").resolve("sbt.autoplugins")
  val recordedMtime =
    Paths.get(System.getProperty("java.io.tmpdir"), s"plugin-descriptor-mtime-${baseDirectory.value.getAbsolutePath.hashCode}")
  Files.writeString(recordedMtime, Files.getLastModifiedTime(descriptor).toString)
}

checkDescriptorMtime := Def.uncached {
  val descriptor = (Compile / resourceManaged).value.toPath.resolve("sbt").resolve("sbt.autoplugins")
  val recordedMtime =
    Paths.get(System.getProperty("java.io.tmpdir"), s"plugin-descriptor-mtime-${baseDirectory.value.getAbsolutePath.hashCode}")
  val expected = Files.readString(recordedMtime)
  val actual = Files.getLastModifiedTime(descriptor)
  assert(actual.toString == expected, s"assembly rewrote $descriptor: $actual")
}

checkDescriptorRebuilt := Def.uncached {
  val descriptor = (Compile / resourceManaged).value.toPath.resolve("sbt").resolve("sbt.autoplugins")
  val recordedMtime =
    Paths.get(System.getProperty("java.io.tmpdir"), s"plugin-descriptor-mtime-${baseDirectory.value.getAbsolutePath.hashCode}")
  val expected = Files.readString(recordedMtime)
  val actual = Files.getLastModifiedTime(descriptor)
  assert(actual.toString != expected, s"assembly did not rewrite $descriptor")
}

setAssemblyMtime := Def.uncached {
  val artifact = (assembly / assemblyOutputPath).value
  Files.setLastModifiedTime(artifact.toPath, expectedAssemblyMtime)
}

checkAssemblyMtime := Def.uncached {
  val artifact = (assembly / assemblyOutputPath).value
  val actual = Files.getLastModifiedTime(artifact.toPath)
  assert(actual == expectedAssemblyMtime, s"assembly rewrote $artifact: $actual")
}

checkAssemblyRebuilt := Def.uncached {
  val artifact = (assembly / assemblyOutputPath).value
  val actual = Files.getLastModifiedTime(artifact.toPath)
  assert(actual != expectedAssemblyMtime, s"assembly did not rebuild $artifact")
}
