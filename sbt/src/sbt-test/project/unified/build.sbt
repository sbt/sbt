lazy val root = (project in file("."))
  .settings(
    cancelable in Global := true,
    scalaVersion in ThisBuild := "2.11.11",
    test in Test := (),
    scalacOptions in console += "-deprecation",
    scalacOptions in (Compile, console) += "-Ywarn-numeric-widen",
    scalacOptions in (projA, Compile, console) += "-feature",
    scalacOptions in (Zero, Zero, console) += "-feature",
    name in (Zero, Compile) := "x",
    name in (projA, Zero, console) := "x"
  )

lazy val projA = (project in file("a"))
