import java.nio.file.Files
import java.nio.file.attribute.FileTime

sbtPlugin := true

val expectedMtime = FileTime.fromMillis(1234L)
val setPackageBinMtime = taskKey[Unit]("Sets the packageBin output mtime")
val checkPackageBinMtime = taskKey[Unit]("Checks that packageBin did not rewrite its output")
val checkPackageBinRebuilt = taskKey[Unit]("Checks that packageBin rebuilt its output")

setPackageBinMtime := {
  val artifact = (Compile / packageBin / artifactPath).value
  Files.setLastModifiedTime(artifact.toPath, expectedMtime)
}

checkPackageBinMtime := {
  val artifact = (Compile / packageBin / artifactPath).value
  val actual = Files.getLastModifiedTime(artifact.toPath)
  assert(actual == expectedMtime, s"packageBin rewrote $artifact: $actual")
}

checkPackageBinRebuilt := {
  val artifact = (Compile / packageBin / artifactPath).value
  val actual = Files.getLastModifiedTime(artifact.toPath)
  assert(actual != expectedMtime, s"packageBin did not rebuild $artifact")
}
