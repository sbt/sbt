Global / localCacheDirectory := baseDirectory.value / "diskcache"

scalaVersion := "3.9.0"

lazy val checkTarget = taskKey[Unit]("Assert BSP items point at the current build target")

lazy val second = (project in file("."))
  .settings(
    name := "repro",
    checkTarget := Def.uncached {
      val expected = (Compile / bspTargetIdentifier).value
      val sources = (Compile / bspBuildTargetSourcesItem).value.target
      val resources = (Compile / bspBuildTargetResourcesItem).value.target
      val javacOptions = (Compile / bspBuildTargetJavacOptionsItem).value.target
      assert(sources == expected, s"sources target = $sources (expected $expected)")
      assert(resources == expected, s"resources target = $resources (expected $expected)")
      assert(javacOptions == expected, s"javacOptions target = $javacOptions (expected $expected)")
    }
  )
