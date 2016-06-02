
lazy val OtherScala = config("other-scala").hide

configs(OtherScala)

libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.11.1" % OtherScala.name

managedClasspath in OtherScala := Classpaths.managedJars(OtherScala, classpathTypes.value, update.value)

// Hack in the scala instance
scalaInstance := {
  val rawJars = (managedClasspath in OtherScala).value.map(_.data)
  val scalaHome = (target.value / "scala-home")
  def removeVersion(name: String): String =
    name.replaceAll("\\-2.11.1", "")
  for(jar <- rawJars) {
    val tjar = scalaHome / s"lib/${removeVersion(jar.getName)}"
    IO.copyFile(jar, tjar)
  }
  IO.listFiles(scalaHome).foreach(f => System.err.println(s" * $f}"))
  ScalaInstance(scalaHome, appConfiguration.value.provider.scalaProvider.launcher)
}


libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.3" % "test"

scalaVersion := "2.11.0"

ivyScala := ivyScala.value map {_.copy(overrideScalaVersion = sbtPlugin.value)}
