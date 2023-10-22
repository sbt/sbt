lazy val intTask = taskKey[Int]("int")

lazy val root = (project in file("."))
  .dependsOn(b, c)
  .settings(
    Compile / intTask := {
      // a sequence of tasks could be joined together
      Seq(b, c)
        .map(p => p / Compile / intTask)
        .join
        .map(as => (1 /: as)(_ + _))
        .value
    }
  )

lazy val b = (project in file("b")).settings(
  Compile / intTask := 1
)

lazy val c = (project in file("c")).settings {
  Compile / intTask := 2
}
