crossPaths := false

TaskKey[Unit]("useJar") := { injar.Test.other; () }
