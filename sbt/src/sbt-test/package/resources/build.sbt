name :== "Main Resources Test"

version :== "0.1"

crossPaths :== false

packageOptions <<= (packageOptions, scalaInstance) map { (opts, si) =>
	def manifestExtra =
	{
		import java.util.jar._
		val mf = new Manifest
		mf.getMainAttributes.put(Attributes.Name.CLASS_PATH, si.libraryJar.getAbsolutePath)
		mf
	}
	Package.JarManifest(manifestExtra) +: opts
}