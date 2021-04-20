import sbt.internal.inc.Analysis
name := "test"

TaskKey[Unit]("checkSame") := (compile in Configurations.Compile map { case analysis: Analysis =>
	analysis.apis.internal foreach { case (_, api) =>
		assert( xsbt.api.SameAPI(api.api, api.api) )
	}
}).value
