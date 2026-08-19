import java.nio.file.Files
import java.nio.file.Paths

sbtPlugin := true

val recordPackageBinMtime = taskKey[Unit]("Records the packageBin output mtime")
val checkPackageBinMtime = taskKey[Unit]("Checks that packageBin did not rewrite its output")
val checkPackageBinRebuilt = taskKey[Unit]("Checks that packageBin rebuilt its output")

recordPackageBinMtime := Def.uncached {
  val artifact = fileConverter.value.toPath((Compile / packageBin / artifactPath).value)
  val recordedMtime = Paths.get(System.getProperty("java.io.tmpdir"), s"package-bin-mtime-${baseDirectory.value.getAbsolutePath.hashCode}")
  Files.writeString(recordedMtime, Files.getLastModifiedTime(artifact).toString)
}

checkPackageBinMtime := Def.uncached {
  val artifact = fileConverter.value.toPath((Compile / packageBin / artifactPath).value)
  val recordedMtime = Paths.get(System.getProperty("java.io.tmpdir"), s"package-bin-mtime-${baseDirectory.value.getAbsolutePath.hashCode}")
  val expected = Files.readString(recordedMtime)
  val actual = Files.getLastModifiedTime(artifact)
  assert(actual.toString == expected, s"packageBin rewrote $artifact: $actual")
}

checkPackageBinRebuilt := Def.uncached {
  val artifact = fileConverter.value.toPath((Compile / packageBin / artifactPath).value)
  val recordedMtime = Paths.get(System.getProperty("java.io.tmpdir"), s"package-bin-mtime-${baseDirectory.value.getAbsolutePath.hashCode}")
  val expected = Files.readString(recordedMtime)
  val actual = Files.getLastModifiedTime(artifact)
  assert(actual.toString != expected, s"packageBin did not rebuild $artifact")
}
