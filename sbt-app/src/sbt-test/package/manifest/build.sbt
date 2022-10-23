import java.util.jar.{Attributes, Manifest}
import Path.makeString

name := "Jar Manifest Test"

version := "0.2"

crossPaths := false

mainClass := Some("jartest.Main")

Compile / packageBin / packageOptions := {
  def manifestExtra = {
    val mf = new Manifest
    mf.getMainAttributes.put(Attributes.Name.CLASS_PATH, makeString(scalaInstance.value.libraryJars))
    mf
  }
  (Compile / packageBin / packageOptions).value :+ Package.JarManifest(manifestExtra)
}
