autoScalaLibrary := false

ivyPaths := (baseDirectory, target)( (dir, t) => IvyPaths(dir, Some(t / "ivy-cache"))).value

ivyScala := ((scalaVersion in update, scalaBinaryVersion in update) { (fv, bv) =>
	Some(IvyScala(fv, bv, Vector.empty, filterImplicit = false, checkExplicit = false, overrideScalaVersion = false))
}).value

InputKey[Unit]("check") := (inputTask { args => 
	(update, args) map {
		case (report, Seq(expected, _*)) =>
			report.allModules.forall(_.revision == expected)
	}
}).evaluated

scalaVersion := "2.9.1"
