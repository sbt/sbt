ThisBuild / scalaVersion := "2.12.17"
def configIvyScala =
  scalaModuleInfo ~= (_ map (_ withCheckExplicit false))

val declared = SettingKey[Boolean]("declared")
lazy val a = project
  .settings(
    libraryDependencies += "org.scala-tools.sbinary" %% "sbinary" % "0.4.0" % "provided",
    configIvyScala,
    update / scalaBinaryVersion := "2.9.0",
  )

lazy val b = project
  .dependsOn(a)
  .settings(
    libraryDependencies := declared((d) =>
      if (d) Seq("org.scala-tools.sbinary" %% "sbinary" % "0.4.0" % "provided")
      else Nil).value,
    declared := baseDirectory((dir) => (dir / "declare.lib").exists).value,
    configIvyScala,
    update / scalaBinaryVersion := "2.9.0"
  )
