autoScalaLibrary := false

ivyPaths := (baseDirectory, target)( (dir, t) => IvyPaths(dir, Some(t / "ivy-cache"))).value

ivyScala := ((scalaVersion in update, scalaBinaryVersion in update) { (fv, bv) =>
  Some(IvyScala(fv, bv, Vector.empty, filterImplicit = false, checkExplicit = false, overrideScalaVersion = false))
}).value

InputKey[Unit]("check") := {
  val args = Def.spaceDelimited().parsed
  val Seq(expected, _*) = args
  update.value.allModules.forall(_.revision == expected)
}

scalaVersion := "2.9.1"
