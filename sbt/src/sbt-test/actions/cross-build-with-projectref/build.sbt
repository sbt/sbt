val b = Project("b", file("b"))
  .settings(
    crossScalaVersions := Seq("2.12.10", "2.13.1"),
    compile in Compile := {
      // We'd like to be able to inspect to this 2.12-only project
      // even while compiling the 2.13 version of 'b'
      (fullClasspath in Compile in ProjectRef(file("."), "c")).value
      (compile in Compile).value
    }
  )

val c = Project("c", file("c"))
  .enablePlugins(SbtPlugin)
  .settings(
    crossScalaVersions := Seq("2.12.10"),
    libraryDependencies += 
      // Some library that is not available on 2.13
      "com.typesafe.akka" %% "akka-actor" % "2.5.0"
  )

val root = Project("reproduce-5497", file("."))
  .aggregate(b, c)
  .settings(
    crossScalaVersions := Nil
  )
