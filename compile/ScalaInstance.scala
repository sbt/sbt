package xsbt

	import java.io.File

/** Represents the source for Scala classes for a given version.  The reason both a ClassLoader and the jars are required
* is that the compiler requires the location of the library/compiler jars on the (boot)classpath and the loader is used
* for the compiler itself.
* The 'version' field is the version used to obtain the Scala classes.  This is typically the version for the maven repository.
* The 'actualVersion' field should be used to uniquely identify the compiler.  It is obtained from the compiler.properties file.*/
final class ScalaInstance(val version: String, val loader: ClassLoader, val libraryJar: File, val compilerJar: File) extends NotNull
{
	/** Gets the version of Scala in the compiler.properties file from the loader.  This version may be different than that given by 'version'*/
	lazy val actualVersion =
	{
		import ScalaInstance.VersionPrefix
		val v = Class.forName("scala.tools.nsc.Properties", true, loader).getMethod("versionString").invoke(null).toString
		if(v.startsWith(VersionPrefix)) v.substring(VersionPrefix.length) else v
	}
}
object ScalaInstance
{
	val VersionPrefix = "version "
	/** Creates a ScalaInstance using the given provider to obtain the jars and loader.*/
	def apply(version: String, launcher: xsbti.Launcher): ScalaInstance =
		apply(version, launcher.getScala(version))
	def apply(version: String, provider: xsbti.ScalaProvider): ScalaInstance =
		new ScalaInstance(version, provider.loader, provider.libraryJar, provider.compilerJar)
}