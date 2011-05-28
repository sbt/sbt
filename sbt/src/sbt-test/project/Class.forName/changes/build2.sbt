crossPaths :== false

TaskKey[Unit]("use-jar") := { injar.Test.other; () }
