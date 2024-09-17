lazy val scala213 = "2.13.3"
lazy val scala212 = "2.12.12"
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
  .jvmPlatform(scalaVersions = Seq(scala213))

lazy val core = (projectMatrix in file("core"))
  .settings(
    check := {
      assert(moduleName.value == "core", s"moduleName is ${moduleName.value}")

      val directs = libraryDependencies.value
      assert(directs.size == 2, s"$directs")
    },
  )
  .jvmPlatform(scalaVersions = Seq(scala213, scala212))
  .configure(addStuff)

lazy val intf = (projectMatrix in file("intf"))
  .settings(
    check := {
      assert(moduleName.value == "intf", s"moduleName is ${moduleName.value}")
    },
  )
  .jvmPlatform(autoScalaLibrary = false)

lazy val core213 = core.jvm(scala213)

def addStuff(p: Project): Project = {
  p.settings(
    libraryDependencies += "junit" % "junit" % "4.12" % Test
  )
}
