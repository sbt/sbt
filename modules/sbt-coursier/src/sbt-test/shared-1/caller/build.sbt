lazy val check = taskKey[Unit]("")

scalaVersion in ThisBuild := "2.12.8"
organization in ThisBuild := "com.example"

lazy val app = (project in file("app"))
  .dependsOn(util)
  .settings(
    name := "app",
    libraryDependencies += "com.chuusai" %% "shapeless" % "2.3.3",
    check := {
      val ur = update.value
      val cr = ur.configuration(Compile).get
      // configuration report must include a module report for subproject dependency
      val coreReport = cr.modules.find(m =>
        m.module.name == "core_2.12"
      ).getOrElse(sys.error("report for core is missing"))
      assert(coreReport.callers.exists(c => c.caller.name == "util_2.12"),
        s"caller on core is missing util: ${coreReport.callers}")

      // configuration report must include a module report for library dependency
      val shapelessReport = cr.modules.find(m =>
        m.module.name == "shapeless_2.12"
      ).getOrElse(sys.error("report for shapeless is missing"))
      assert(shapelessReport.callers.exists(c => c.caller.name == "app_2.12"),
        s"caller on shapeless is missing self module (app): ${shapelessReport.callers}")
    }
  )

lazy val util = (project in file("util"))
  .dependsOn(core)
  .settings(
    name := "util"
  )

lazy val core = (project in file("core"))
  .settings(
    name := "core"
  )
