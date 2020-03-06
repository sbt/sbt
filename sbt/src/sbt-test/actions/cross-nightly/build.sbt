scalaVersion in ThisBuild := "2.7.7"

scalaVersion := "2.9.1"

resolvers += "scala-integration" at
  "https://scala-ci.typesafe.com/artifactory/scala-integration/"

InputKey[Unit]("check") := {
  val twotwelve = """2\.12\.\d+-bin-[0-9a-fA-F]{7}""".r
  val twothirteen = """2\.13\.\d+-bin-[0-9a-fA-F]{7}""".r
  val args = Def.spaceDelimited().parsed
  val vers = scalaVersion.value
  args(0) match {
    case "2.12" => assert(twotwelve.findFirstMatchIn(vers).isDefined, s"$vers doesn't match")
    case "2.13" => assert(twothirteen.findFirstMatchIn(vers).isDefined, s"$vers doesn't match")
  }
}
