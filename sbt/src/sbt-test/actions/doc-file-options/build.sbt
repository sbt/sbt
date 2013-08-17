val newContents = "bbbbbbbbb"

val rootContentFile = "root.txt"

scalaVersion := "2.10.2"

scalacOptions in (Compile, doc) := Seq("-doc-root-content", rootContentFile)

TaskKey[Unit]("changeRootContent") := {
	IO.write(file(rootContentFile), newContents)
}

TaskKey[Unit]("check") := {
	val packageHtml = (target in Compile in doc).value / "package.html"
	assert(IO.read(packageHtml).contains(newContents), s"does not contains ${newContents} in ${packageHtml}" )
}
