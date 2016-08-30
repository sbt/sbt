autoScalaLibrary := false

ivyPaths := (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache"))).value

ivyScala := ((scalaVersion in update, scalaBinaryVersion in update) { (fv, bv) =>
	Some(new IvyScala(fv, bv, Nil, filterImplicit = false, checkExplicit = false, overrideScalaVersion = false))
}).value

InputKey[Unit]("check") := (inputTask { args => 
	(update, args) map {
		case (report, Seq(expected, _*)) =>
			report.allModules.forall(_.revision == expected)
	}
}).evaluated

scalaVersion := "2.9.1"