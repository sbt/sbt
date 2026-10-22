import complete.DefaultParsers.*

Global / localCacheDirectory := baseDirectory.value / "diskcache"

scalaVersion := "3.9.0"

lazy val checkOpts = inputKey[Unit]("Assert Compile / scalacOptions equals the given options")

lazy val root = (project in file("."))
  .settings(
    B1.buildSettings,
    checkOpts := Def.uncached {
      val expected = spaceDelimited("<opt>").parsed
      val actual = (Compile / scalacOptions).value
      assert(actual == expected, s"scalacOptions = $actual (expected $expected)")
    }
  )
