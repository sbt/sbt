	import java.util.jar.{Attributes, Manifest}
	import Path.makeString
	import Keys.{Package, ScalaInstance}
	import Configurations.Compile

Name :== "Jar Manifest Test"

Version :== "0.2"

CrossPaths :== false

MainClass :== Some("jartest.Main")

PackageOptions in (Compile, Package) <<= (PackageOptions in (Compile, Package), ScalaInstance) map { (opts, si) =>
	def manifestExtra =
	{
		val mf = new Manifest
		mf.getMainAttributes.put(Attributes.Name.CLASS_PATH, makeString(si.libraryJar :: Nil) )
		mf
	}
	opts :+ sbt.Package.JarManifest(manifestExtra)
}
