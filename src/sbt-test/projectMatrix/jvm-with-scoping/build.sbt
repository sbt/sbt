lazy val check = taskKey[Unit]("")

lazy val root = (project in file("."))
  .aggregate(core.projectRefs ++ app.projectRefs: _*)
  .settings(
  )

lazy val app = (projectMatrix in file("app"))
  .aggregate(core, intf)
  .dependsOn(core % Compile, intf % "compile->compile;test->test")
  .settings(
    name := "app"
  )
  .jvmPlatform(scalaVersions = Seq("2.12.8"))

lazy val core = (projectMatrix in file("core"))
  .settings(
    check := {
      assert(moduleName.value == "core", s"moduleName is ${moduleName.value}")

      val directs = libraryDependencies.value
      assert(directs.size == 2, s"$directs")
    },
  )
  .jvmPlatform(scalaVersions = Seq("2.12.8", "2.11.12"))
  .configure(addStuff)

lazy val intf = (projectMatrix in file("intf"))
  .settings(
    check := {
      assert(moduleName.value == "intf", s"moduleName is ${moduleName.value}")
    },
  )
  .jvmPlatform(autoScalaLibrary = false)

lazy val core212 = core.jvm("2.12.8")

def addStuff(p: Project): Project = {
  p.settings(
    libraryDependencies += "junit" % "junit" % "4.12" % Test
  )
}
