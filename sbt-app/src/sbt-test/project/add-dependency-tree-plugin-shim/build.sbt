import sbtassembly.AssemblyPlugin.autoImport._

assembly / assemblyJarName := "plugin-survived.jar"

TaskKey[Unit]("check") := ()
