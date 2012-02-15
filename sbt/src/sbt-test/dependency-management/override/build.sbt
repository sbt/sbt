autoScalaLibrary := false

libraryDependencies += "junit" % "junit" % "4.5" % "test"

InputKey[Unit]("check") <<= inputTask { args => 
	(update, args) map {
		case (report, Seq(expected, _*)) =>
			report.allModules.forall(_.revision == expected)
	}
}
