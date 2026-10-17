ThisBuild / scalaVersion := "2.13.16"
ThisBuild / usePipelining := true
lazy val a = project.settings(Test / sourceGenerators += Def.task[Seq[File]] { sys.error("test source generator failed") })
lazy val b = project.dependsOn(a % "compile->compile;test->test")
