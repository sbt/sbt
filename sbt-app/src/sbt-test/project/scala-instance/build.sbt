import Configurations.{ ScalaTool, ScalaDocTool, ZincTool }

@transient
lazy val check = taskKey[Unit]("")
lazy val scala213 = "2.13.16"
scalaVersion := scala213
autoScalaLibrary := false
managedScalaInstance := false
ivyConfigurations ++= List(ScalaTool, ScalaDocTool, ZincTool)
libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % scala213,
  "org.scala-lang" % "scala-compiler" % scala213 % ScalaTool,
  "org.scala-lang" % "scala-compiler" % scala213 % ScalaDocTool,
  "org.scala-lang" % "scala2-sbt-bridge" % scala213 % ZincTool,
)
check := {
  val si = scalaInstance.value
  assert(si.version == scala213, s"'${si.version}' was not '$scala213'")
}
