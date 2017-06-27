name := "Main Resources Test"

version := "0.1"

crossPaths := false

packageOptions := {
  def manifestExtra = {
    import java.util.jar._
    val mf = new Manifest
    mf.getMainAttributes.put(Attributes.Name.CLASS_PATH, scalaInstance.value.libraryJar.getAbsolutePath)
    mf
  }
  Package.JarManifest(manifestExtra) +: packageOptions.value
}
