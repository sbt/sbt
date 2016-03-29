def configIvyScala =
  ivyScala ~= { _.map(_.copy(checkExplicit = false)) }

val declared = SettingKey[Boolean]("declared")
lazy val a = project.
  settings(
    libraryDependencies += "org.scala-tools.sbinary" %% "sbinary" % "0.4.0" % "provided",
    configIvyScala,
    scalaBinaryVersion in update := "2.9.0"
  )

lazy val b = project.
  dependsOn(a).
  settings(
    libraryDependencies <<= declared(d => if(d) Seq("org.scala-tools.sbinary" %% "sbinary" % "0.4.0" % "provided") else Nil),
    declared <<= baseDirectory(_ / "declare.lib" exists),
    configIvyScala,
    scalaBinaryVersion in update := "2.9.0"
  )
