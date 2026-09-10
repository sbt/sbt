import Path.makeString

name := "Main Resources Test"
version := "0.1"
crossPaths := false
scalaVersion := "3.3.1"

packageOptions := {
  def manifestExtra = {
    import java.util.jar._
    val mf = new Manifest
    mf.getMainAttributes.put(Attributes.Name.CLASS_PATH, makeString(scalaInstance.value.libraryJars.toSeq))
    mf
  }
  Package.JarManifest(manifestExtra) +: packageOptions.value
}

Compile / resourceGenerators += Def.task {
  val file = (Compile / resourceManaged).value / "jartest" / "generated_resource_test"
  IO.write(file, "This is a generated resource to test that sbt includes generated resources in the packaged jar.")
  Seq(file)
}

TaskKey[Unit]("checkGeneratedResourceInJar") := {
  val converter = fileConverter.value
  val jarFile = converter.toPath((Compile / packageBin).value).toFile
  val jar = new java.util.jar.JarFile(jarFile)
  try {
    if (jar.getJarEntry("jartest/generated_resource_test") == null)
      sys.error(s"jartest/generated_resource_test not found in $jarFile")
  } finally jar.close()
}
