scalaVersion in ThisBuild := "2.7.7"

scalaVersion := "2.9.1"

scalaVersion in update <<= scalaVersion {
  case "2.9.1" => "2.9.0-1"
  case "2.8.2" => "2.8.1"
  case x => x
}

InputKey[Unit]("check") <<= inputTask { argsT =>
  (argsT, scalaVersion in ThisBuild, scalaVersion, scalaVersion in update) map { (args, svTB, svP, svU) =>
    def check(label: String, i: Int, actual: String) = assert(args(i) == actual, "Expected " + label + "='" + args(i) + "' got '" + actual + "'")
    check("scalaVersion in ThisBuild", 0, svTB)
    check("scalaVersion", 1, svP)
    check("scalaVersion in update", 2, svU)
  }
}
