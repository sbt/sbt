libraryDependencies += "org.scala-sbt" % "sbt" % sbtVersion.value


lazy val expectErrorNotCrash = taskKey[Unit]("Ensures that sbt properly set types on Trees so that the compiler doesn't crash on a bad reference to .value, but gives a proper error instead.")

expectErrorNotCrash := {
        val fail = (compileIncremental in Compile).failure.value
	fail.directCause match {
		case Some(x: xsbti.CompileFailed) => ()
		case _ => sys.error("Compiler crashed instead of providing a compile-time-only exception.")
	}
}
