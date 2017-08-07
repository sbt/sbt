val baseSbt = "0.13"

val buildCrossList = List("2.10.6", "2.11.11", "2.12.2")
scalaVersion in ThisBuild := "2.10.6"
crossScalaVersions in ThisBuild := buildCrossList

lazy val root = (project in file("."))
  .settings(
    sbtPlugin := true,

    TaskKey[Unit]("check") := mkCheck("2.10", "0.13").value,
    TaskKey[Unit]("check2") := mkCheck("2.12", "1.0").value
  )

lazy val app = (project in file("app"))

def mkCheck(scalaBinV: String, sbtBinVer: String) = Def task {
  val crossV = (sbtVersion in pluginCrossBuild).value
  val sv = projectID.value.extraAttributes("e:scalaVersion")
  assert(sbtVersion.value startsWith baseSbt, s"Wrong sbt version: ${sbtVersion.value}")
  assert(sv == scalaBinV, s"Wrong e:scalaVersion: $sv")
  assert(scalaBinaryVersion.value == scalaBinV, s"Wrong Scala binary version: ${scalaBinaryVersion.value}")
  assert(crossV startsWith sbtBinVer, s"Wrong `sbtVersion in pluginCrossBuild`: $crossV")

  // crossScalaVersions in app should not be affected, per se or after ^^
  val appCrossScalaVersions = (crossScalaVersions in app).value.toList
  val appScalaVersion = (scalaVersion in app).value
  assert(appCrossScalaVersions == buildCrossList, s"Wrong `crossScalaVersions in app`: $appCrossScalaVersions")
  assert(appScalaVersion startsWith "2.10", s"Wrong `scalaVersion in app`: $appScalaVersion")
}
