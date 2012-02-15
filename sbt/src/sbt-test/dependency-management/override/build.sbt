scalaVersion := "2.9.1"

InputKey[Unit]("check") <<= inputTask { args => 
	(update, args) map {
		case (report, Seq(expected, _*)) =>
			report.allModules.forall(_.revision == expected)
	}
}
