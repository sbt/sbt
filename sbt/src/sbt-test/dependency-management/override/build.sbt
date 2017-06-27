autoScalaLibrary := false

ivyPaths := IvyPaths(baseDirectory.value, Some(target.value / "ivy-cache"))

ivyScala := Some(IvyScala(
  (scalaVersion in update).value,
  (scalaBinaryVersion in update).value,
  Vector.empty,
  filterImplicit = false,
  checkExplicit = false,
  overrideScalaVersion = false
))

InputKey[Unit]("check") := {
  val args = Def.spaceDelimited().parsed
  val Seq(expected, _*) = args
  update.value.allModules.forall(_.revision == expected)
}

scalaVersion := "2.9.1"
