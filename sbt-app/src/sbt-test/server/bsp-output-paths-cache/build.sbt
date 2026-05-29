import sbt.internal.bsp.OutputPathsItem

ThisBuild / scalaVersion := "3.8.2"

name := "bsp-output-paths-cache"

Compile / target := baseDirectory.value / "target-a"

@transient
lazy val checkProjectA = taskKey[Unit]("")

@transient
lazy val checkProjectB = taskKey[Unit]("")

def checkOutputPath(item: OutputPathsItem, expectedSegment: String): Unit = {
  val actual = item.outputPaths.map(_.uri.toString).mkString("\n")
  if (!actual.contains(expectedSegment)) {
    sys.error(
      s"stale bspBuildTargetOutputPathsItem: expected segment $expectedSegment in output paths, got: $actual"
    )
  }
}

checkProjectA := Def.uncached {
  checkOutputPath((Compile / bspBuildTargetOutputPathsItem).value, "target-a")
}

checkProjectB := Def.uncached {
  checkOutputPath((Compile / bspBuildTargetOutputPathsItem).value, "target-b")
}
