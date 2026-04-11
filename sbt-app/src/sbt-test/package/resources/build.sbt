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
