import java.nio.file.Files

Global / localCacheDirectory := baseDirectory.value / "diskcache"

scalaVersion := "3.9.0"

@transient
lazy val deleteAnalysis = taskKey[Unit]("")
@transient
lazy val checkLinks = taskKey[Unit]("")
@transient
lazy val checkRegular = taskKey[Unit]("")

def aProducts(dir: File) = Seq("A.class", "A.tasty").map(dir.toPath.resolve)

deleteAnalysis := IO.delete((Compile / compileAnalysisFile).value)

checkLinks := aProducts((Compile / classDirectory).value).foreach: p =>
  assert(Files.isSymbolicLink(p), s"$p is not a symlink")

checkRegular := aProducts((Compile / classDirectory).value).foreach: p =>
  assert(Files.isRegularFile(p) && !Files.isSymbolicLink(p), s"$p is not a regular file")
