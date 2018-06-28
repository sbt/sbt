scalaVersion := "2.11.8"

val buildInfo = taskKey[Seq[File]]("generates the build info")
buildInfo := {
  val file = sourceManaged.value / "BuildInfo.scala"
  IO.write(file, "object BuildInfo")
  file :: Nil
}

sourceGenerators in Compile += buildInfo
sourceGenerators in Compile += Def.task { Nil }
