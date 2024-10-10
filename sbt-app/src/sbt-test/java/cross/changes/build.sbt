import complete.DefaultParsers._

val check = inputKey[Unit]("Runs the check")

lazy val root = (project in file("."))
  .settings(
    ThisBuild / scalaVersion := "2.12.20",
    crossJavaVersions := List("1.8", "10"),

    // read out.txt and see if it starts with the passed in number
    check := {
      val arg1: Int = (Space ~> NatBasic).parsed
      file("out.txt") match {
        case out if out.exists =>
          IO.readLines(out).headOption match {
            case Some(v) if v startsWith arg1.toString => ()
            case Some(v) if v startsWith s"1.$arg1"    => ()
            case x => sys.error(s"unexpected value: $x")
          }
        case out => sys.error(s"$out doesn't exist")
      }
    },

    Compile / run / fork := true,
  )
