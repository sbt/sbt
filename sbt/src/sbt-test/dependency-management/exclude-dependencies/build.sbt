lazy val check = taskKey[Unit]("check")

val dispatch = "net.databinder.dispatch" %% "dispatch-core" % "0.11.2"
val repatchTwitter = "com.eed3si9n" %% "repatch-twitter-core" % "dispatch0.11.1_0.1.0"

lazy val a = (project in file("a")).
  settings(
    scalaVersion := "2.11.4",
    libraryDependencies += dispatch,
    excludeDependencies += "org.slf4j"
  )

lazy val b = (project in file("b")).
  settings(
    scalaVersion := "2.11.4",
    libraryDependencies += repatchTwitter,
    excludeDependencies += "net.databinder.dispatch" %% "dispatch-core"
  )

lazy val root = (project in file(".")).
  settings(
    check := {
      (update in a).value
      (update in b).value
      val acp = (externalDependencyClasspath in Compile in a).value.sortBy {_.data.getName}
      val bcp = (externalDependencyClasspath in Compile in b).value.sortBy {_.data.getName}

      if (acp exists { _.data.getName contains "slf4j-api-1.7.5.jar" }) {
        sys.error("slf4j-api-1.7.5.jar found when it should NOT be included: " + acp.toString)
      }
      if (bcp exists { _.data.getName contains "dispatch-core_2.11-0.11.1.jar" }) {
        sys.error("dispatch-core_2.11-0.11.1.jar found when it should NOT be included: " + bcp.toString)
      }
    }
  )