import sbt.internal.inc.Analysis
name := "test"
ThisBuild / scalaVersion := "2.12.17"

TaskKey[Unit]("checkSame") := ((Configurations.Compile / compile) map {
  case analysis: Analysis =>
    analysis.apis.internal foreach { case (_, api) =>
      assert( xsbt.api.SameAPI(api.api, api.api) )
    }
}).value
