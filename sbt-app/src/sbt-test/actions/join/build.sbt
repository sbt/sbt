lazy val intTask = taskKey[Int]("int")

lazy val root = (project in file(".")).
  dependsOn(b, c).
  settings(
    intTask in Compile := {
      // a sequence of tasks could be joined together
      Seq(b, c).map(p => intTask in (p, Compile)).join.map( as => (1 /: as)(_ + _) ).value
    }
  )

lazy val b = (project in file("b")).
  settings(
    intTask in Compile := 1
  )

lazy val c = (project in file("c")).
  settings{
    intTask in Compile := 2
  }
