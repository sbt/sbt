crossScalaVersions := Seq("2.11.12", "2.12.21")

val versionsFile = settingKey[File]("file recording the versions compileAll ran under")
versionsFile := baseDirectory.value / "versions.txt"

commands += Command.command("compileAll") { s =>
  val extracted = Project.extract(s)
  IO.append(extracted.get(versionsFile), extracted.get(scalaVersion) + "\n")
  s
}

TaskKey[Unit]("check") := {
  val versions = IO.read(versionsFile.value).linesIterator.toList
  assert(versions == List("2.11.12", "2.12.21"), s"got $versions")
}
