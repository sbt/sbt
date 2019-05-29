import java.util.jar.{Attributes, Manifest}
import Path.makeString

name := "Jar Manifest Test"

version := "0.2"

crossPaths := false

mainClass := Some("jartest.Main")

packageOptions in (Compile, packageBin) := {
  def manifestExtra = {
    val mf = new Manifest
    mf.getMainAttributes.put(Attributes.Name.CLASS_PATH, makeString(scalaInstance.value.libraryJars))
    mf
  }
  (packageOptions in (Compile, packageBin)).value :+ Package.JarManifest(manifestExtra)
}
