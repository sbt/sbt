	import java.util.jar.{Attributes, Manifest}
	import Path.makeString

name :== "Jar Manifest Test"

version :== "0.2"

crossPaths :== false

mainClass :== Some("jartest.Main")

packageOptions in (Compile, packageBin) <<= (packageOptions in (Compile, packageBin), scalaInstance) map { (opts, si) =>
	def manifestExtra =
	{
		val mf = new Manifest
		mf.getMainAttributes.put(Attributes.Name.CLASS_PATH, makeString(si.libraryJar :: Nil) )
		mf
	}
	opts :+ Package.JarManifest(manifestExtra)
}
