Name :== "Main Resources Test"

Version :== "0.1"

CrossPaths :== false

PackageOptions <<= (PackageOptions, Keys.ScalaInstance) map { (opts, si) =>
	def manifestExtra =
	{
		import java.util.jar._
		val mf = new Manifest
		mf.getMainAttributes.put(Attributes.Name.CLASS_PATH, si.libraryJar.getAbsolutePath)
		mf
	}
	sbt.Package.JarManifest(manifestExtra) +: opts
}