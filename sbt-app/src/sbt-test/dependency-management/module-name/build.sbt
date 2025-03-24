name := "Demo"

moduleName := "asdf"

crossPaths := false

TaskKey[Unit]("checkName") := Def task {
  val converter = fileConverter.value
  val vf = (Compile / packageBin).value
  val path = converter.toPath(vf).toAbsolutePath.toString
  val module = moduleName.value
  val n = name.value
  assert(path contains module, s"Path $path did not contain module name $module")
  assert(!path.contains(n), s"Path $path contained $n")
}
