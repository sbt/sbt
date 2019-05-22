ThisBuild / crossScalaVersions := Seq("2.12.8")

lazy val root = (project in file("."))
  .aggregate(foo)

lazy val foo = (project in file("foo"))
  .settings(
    name := "foo",
    libraryDependencies += "com.novocode" % "junit-interface" % "0.8" % "test"
  )
