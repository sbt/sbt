ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

autoScalaLibrary := false

scalaModuleInfo := Some(sbt.librarymanagement.ScalaModuleInfo(
  (update / scalaVersion).value,
  (update / scalaBinaryVersion).value,
  Vector.empty,
  checkExplicit = false,
  filterImplicit = false,
  overrideScalaVersion = false
))

InputKey[Unit]("check") := {
  val args = Def.spaceDelimited().parsed
  val Seq(expected, _*) = args
  update.value.allModules.forall(_.revision == expected)
}

scalaVersion := "2.9.1"
