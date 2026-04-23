import Configurations.{ ScalaTool, ScalaDocTool }

@transient
lazy val check = taskKey[Unit]("")
lazy val scala213 = "2.13.16"
scalaVersion := scala213
autoScalaLibrary := false
managedScalaInstance := false
ivyConfigurations ++= List(ScalaTool, ScalaDocTool)
libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % scala213,
  "org.scala-lang" % "scala-compiler" % scala213 % ScalaTool,
  "org.scala-lang" % "scala-compiler" % scala213 % ScalaDocTool,
)
check := {
  val si = scalaInstance.value
  assert(si.version == scala213, s"'${si.version}' was not '$scala213'")
}
