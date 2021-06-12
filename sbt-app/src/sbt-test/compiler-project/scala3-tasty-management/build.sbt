import xsbti.compile.TastyFiles

ThisBuild / scalaVersion := "3.0.0-M3"

TaskKey[Unit]("check") := {
  assert((Compile / auxiliaryClassFiles).value == Seq(TastyFiles.instance))
  assert((Test / auxiliaryClassFiles).value == Seq(TastyFiles.instance))
}
