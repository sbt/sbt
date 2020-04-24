ThisBuild / scalaVersion := "2.11.12"

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.12.11",

    update / scalaVersion := {
      scalaVersion.value match {
        case "2.12.11" => "2.12.10"
        case "2.11.12" => "2.11.11"
        case x         => x
      }
    },

    InputKey[Unit]("check") := {
      val args = Def.spaceDelimited().parsed
      def checkV(label: String, i: Int, actual: String) =
        assert(args(i) == actual, s"Expected $label='${args(i)}' got '$actual'")

      checkV("ThisBuild / scalaVersion", 0, (ThisBuild / scalaVersion).value)
      checkV("scalaVersion", 1, scalaVersion.value)
      checkV("update / scalaVersion", 2, (update / scalaVersion).value)
    }
  )
