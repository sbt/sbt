libraryDependencies ++= {
  if (ScalafmtVersion.value == "2.0.4") {
    val sbtV = (sbtBinaryVersion in pluginCrossBuild).value
    val scalaV = (scalaBinaryVersion in update).value
    val dep = "org.scalameta" % "sbt-scalafmt" % ScalafmtVersion.value
    sbt.Defaults.sbtPluginExtra(dep, sbtV, scalaV) :: Nil
  } else Nil
}
