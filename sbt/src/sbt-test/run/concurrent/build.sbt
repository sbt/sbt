lazy val runTest = taskKey[Unit]("Run the test applications.")

def runTestTask(pre: Def.Initialize[Task[Unit]]) =
	runTest := {
		val _ = pre.value
		val r = (runner in (Compile, run)).value
		val cp = (fullClasspath in Compile).value
		val main = (mainClass in Compile).value getOrElse sys.error("No main class found")
		val args = baseDirectory.value.getAbsolutePath :: Nil
		r.run(main, cp.files, args, streams.value.log).get
	}

lazy val b = project.settings(
	runTestTask( waitForCStart ),
	runTest := {
		val _ = runTest.value
		val cFinished = (baseDirectory in c).value / "finished"
		assert( !cFinished.exists, "C finished before B")
		IO.touch(baseDirectory.value / "finished")
	}
)

lazy val c = project.settings( runTestTask( Def.task() ) )

// need at least 2 concurrently executing tasks to proceed
concurrentRestrictions in Global := Seq(
	Tags.limitAll(math.max(EvaluateTask.SystemProcessors, 2) )
)

def waitForCStart =
	Def.task {
		waitFor( (baseDirectory in c).value / "started" )
	}

def waitFor(f: File): Unit = {
	if(!f.exists) {
		Thread.sleep(300)
		waitFor(f)
	}
}
