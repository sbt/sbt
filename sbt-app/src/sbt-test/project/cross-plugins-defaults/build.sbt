val baseSbt = "1."

val buildCrossList = List("2.10.7", "2.11.12", "2.12.20")
scalaVersion in ThisBuild := "2.12.20"
crossScalaVersions in ThisBuild := buildCrossList

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.7.0")

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    TaskKey[Unit]("check") := mkCheck("2.12", "1.0", "1.").value,
    TaskKey[Unit]("check2") := mkCheck("2.10", "0.13", "0.13").value,
  )

lazy val app = (project in file("app"))

def mkCheck(scalaBinV: String, sbtBinVer: String, sbtVerPrefix: String) = Def.task {
  val crossV = (sbtVersion in pluginCrossBuild).value
  val crossBinV = (sbtBinaryVersion in pluginCrossBuild).value
  val sv = projectID.value.extraAttributes("e:scalaVersion")
  assert(sbtVersion.value startsWith baseSbt, s"Wrong sbt version: ${sbtVersion.value}")
  assert(sv == scalaBinV, s"Wrong e:scalaVersion: $sv")
  assert(scalaBinaryVersion.value == scalaBinV, s"Wrong Scala binary version: ${scalaBinaryVersion.value}")
  assert(crossV startsWith sbtVerPrefix, s"Wrong `sbtVersion in pluginCrossBuild`: $crossV")

  val ur = update.value
  val cr = ur.configuration(Compile).get
  val mr = cr.modules.find(mr => mr.module.organization == "com.eed3si9n" && mr.module.name == "sbt-buildinfo").get
  val plugSv = mr.module.extraAttributes("scalaVersion")
  val plugSbtV = mr.module.extraAttributes("sbtVersion")
  assert(plugSv == scalaBinV, s"Wrong plugin scalaVersion: $plugSv")
  assert(plugSbtV == sbtBinVer, s"Wrong plugin sbtVersion: $plugSbtV")

  // crossScalaVersions in app should not be affected, per se or after ^^
  val appCrossScalaVersions = (crossScalaVersions in app).value.toList
  val appScalaVersion = (scalaVersion in app).value
  assert(appCrossScalaVersions == buildCrossList, s"Wrong `crossScalaVersions in app`: $appCrossScalaVersions")
  assert(appScalaVersion startsWith "2.12", s"Wrong `scalaVersion in app`: $appScalaVersion")
}
