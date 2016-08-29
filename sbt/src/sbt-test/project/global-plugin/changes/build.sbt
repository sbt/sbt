lazy val check = taskKey[Unit]("Verifies that the junit dependency has the newer version (4.8)")

lazy val proj = (project in file(".")).
  settings(
    name := "my-test-proj",
    organization := "com.example",
    check := (update map checkVersion).value,
    version := "0.1.0-SNAPSHOT"
  )

def checkVersion(report: UpdateReport): Unit = {
  for(mod <- report.allModules) {
    if(mod.name == "junit") assert(mod.revision == "4.8", s"JUnit version (${mod.revision}) does not have the correct version")
  }
}
