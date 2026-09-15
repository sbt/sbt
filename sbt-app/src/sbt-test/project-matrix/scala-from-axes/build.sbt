lazy val check = taskKey[Unit]("")

lazy val show = Def.setting {
  val axes = virtualAxes.value.map(_.idSuffix).mkString(",")
  val sv = scalaVersion.value == "2.13.18"
  s"$axes|$sv|${autoScalaLibrary.value}|${crossPaths.value}"
}

// neither a version nor a Scala axis: a Java row
lazy val java = (projectMatrix in file("java")).jvmPlatform(
  autoScalaLibrary = false,
  scalaVersions = Nil,
  settings = Seq(check := assert(show.value == "JVM|false|false|false", show.value)),
)

// no versions: the axes carry the Scala one, and the settings stand in for the row function
lazy val fromAxes = (projectMatrix in file("fromAxes")).customRow(
  axisValues = Seq(VirtualAxis.jvm, VirtualAxis.scalaABIVersion("2.13.18")),
  process = Seq(check := assert(show.value == "JVM,2_13|true|true|true", show.value)),
)

// versions given: they are the rows, whatever the flag says
lazy val both = (projectMatrix in file("both")).customRow(
  autoScalaLibrary = false,
  scalaVersions = Seq("2.13.18"),
  axisValues = Seq(VirtualAxis.jvm),
  process = _.settings(check := assert(show.value == "JVM,2_13|true|true|true", show.value)),
)

lazy val root = (project in file("."))
  .settings(check := {
    val ids = Seq(java, fromAxes, both).map(_.allProjects().map(_._1.id).mkString(","))
    assert(ids == Seq("java", "fromAxes2_13", "both2_13"), ids.toString)
  })
