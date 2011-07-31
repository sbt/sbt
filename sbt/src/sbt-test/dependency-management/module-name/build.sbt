name := "Demo"

moduleName := "asdf"

crossPaths := false

TaskKey[Unit]("check-name") <<= (moduleName, name, packageBin in Compile) map { (module, n, f) =>
  val path = f.getAbsolutePath 
  assert(path contains module, "Path " + path + " did not contain module name " + module)
  assert(!path.contains(n), "Path " + path + " contained " + n)
}
