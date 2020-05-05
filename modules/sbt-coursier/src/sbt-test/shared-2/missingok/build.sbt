scalaVersion := "2.13.2"
libraryDependencies ++= Seq(
  "com.chuusai" %% "shapeless" % "2.3.3",
  // non-existing
  "org.webjars" % "npm" % "0.0.99"
)
updateConfiguration := updateConfiguration.value.withMissingOk(true)

lazy val check = taskKey[Unit]("")

check := {
  val updateReport = update.value
  val updateClassifiersReport = updateClassifiers.value

  val compileReport = updateReport
    .configuration(Compile)
    .getOrElse {
      sys.error("Compile configuration not found in update report")
    }

  val compileClassifiersReport = updateClassifiersReport
    .configuration(Compile)
    .getOrElse {
      sys.error("Compile configuration not found in update classifiers report")
    }

  val shapelessModule = compileReport
    .modules
    .find(_.module.name == "shapeless_2.13")
    .getOrElse {
      sys.error(s"shapeless module not found in ${compileReport.modules.map(_.module)}")
    }

  val shapelessClassifiersModule = compileClassifiersReport
    .modules
    .find(_.module.name == "shapeless_2.13")
    .getOrElse {
      sys.error(s"shapeless module not found in ${compileClassifiersReport.modules.map(_.module)}")
    }
}
