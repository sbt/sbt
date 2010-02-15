package xsbt

	import java.io.File

/** Represents the source for Scala classes for a given version.  The reason both a ClassLoader and the jars are required
* is that the compiler requires the location of the library/compiler jars on the (boot)classpath and the loader is used
* for the compiler itself.
* The 'version' field is the version used to obtain the Scala classes.  This is typically the version for the maven repository.
* The 'actualVersion' field should be used to uniquely identify the compiler.  It is obtained from the compiler.properties file.*/
final class ScalaInstance(val version: String, val loader: ClassLoader, val libraryJar: File, val compilerJar: File) extends NotNull
{
	require(version.indexOf(' ') < 0, "Version cannot contain spaces (was '" + version + "')")
	def jars = libraryJar :: compilerJar :: Nil
	/** Gets the version of Scala in the compiler.properties file from the loader.  This version may be different than that given by 'version'*/
	lazy val actualVersion = ScalaInstance.actualVersion(loader)(" version " + version)
	override def toString = "Scala instance{version label " + version + ", actual version " + actualVersion + ", library jar: " + libraryJar + ", compiler jar: " + compilerJar + "}"
}
object ScalaInstance
{
	val VersionPrefix = "version "
	/** Creates a ScalaInstance using the given provider to obtain the jars and loader.*/
	def apply(version: String, launcher: xsbti.Launcher): ScalaInstance =
		apply(version, launcher.getScala(version))
	def apply(version: String, provider: xsbti.ScalaProvider): ScalaInstance =
		new ScalaInstance(version, provider.loader, provider.libraryJar, provider.compilerJar)

	def apply(scalaHome: File, launcher: xsbti.Launcher): ScalaInstance =
		apply(libraryJar(scalaHome), compilerJar(scalaHome), launcher)
	def apply(version: String, scalaHome: File, launcher: xsbti.Launcher): ScalaInstance =
		apply(version, libraryJar(scalaHome), compilerJar(scalaHome), launcher)
	def apply(libraryJar: File, compilerJar: File, launcher: xsbti.Launcher): ScalaInstance =
	{
		val loader = scalaLoader(launcher, libraryJar, compilerJar)
		val version = actualVersion(loader)(" (library jar  " + libraryJar.getAbsolutePath + ")")
		new ScalaInstance(version, loader, libraryJar, compilerJar)
	}
	def apply(version: String, libraryJar: File, compilerJar: File, launcher: xsbti.Launcher): ScalaInstance =
		new ScalaInstance(version, scalaLoader(launcher, libraryJar, compilerJar), libraryJar, compilerJar)

	private def compilerJar(scalaHome: File) = scalaJar(scalaHome, "scala-compiler.jar")
	private def libraryJar(scalaHome: File) = scalaJar(scalaHome, "scala-library.jar")
	def scalaJar(scalaHome: File, name: String)  =  new File(scalaHome, "lib" + File.separator + name)

	/** Gets the version of Scala in the compiler.properties file from the loader.*/
	private def actualVersion(scalaLoader: ClassLoader)(label: String) =
	{
		val v = try { Class.forName("scala.tools.nsc.Properties", true, scalaLoader).getMethod("versionString").invoke(null).toString }
		catch { case cause: Exception => throw new InvalidScalaInstance("Incompatible Scala instance: " + label, cause) }
		if(v.startsWith(VersionPrefix)) v.substring(VersionPrefix.length) else v
	}

	import java.net.{URL, URLClassLoader}
	private def scalaLoader(launcher: xsbti.Launcher, jars: File*): ClassLoader =
		new URLClassLoader(jars.map(_.toURI.toURL).toArray[URL], launcher.topLoader)
}
class InvalidScalaInstance(message: String, cause: Throwable) extends RuntimeException(message, cause)