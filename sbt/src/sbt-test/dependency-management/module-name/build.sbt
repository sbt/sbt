name := "Demo"

moduleName := "asdf"

crossPaths := false

TaskKey[Unit]("checkName") := Def task {
  val path = (packageBin in Compile).value.getAbsolutePath
  val module = moduleName.value
  val n = name.value
  assert(path contains module, s"Path $path did not contain module name $module")
  assert(!path.contains(n), s"Path $path contained $n")
}
