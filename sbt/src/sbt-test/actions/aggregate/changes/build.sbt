lazy val root = (project in file(".")).
  aggregate((if(file("aggregate").exists) Seq(sub: sbt.ProjectReference) else Nil): _*)

lazy val sub = (project in file("sub")).
  aggregate(sub2)

lazy val sub2 = (project in file("sub") / "sub")
