/* sbt -- Simple Build Tool
 * Copyright 2009, 2010 Mark Harrah
 */
package sbt

	import java.io.File

/** Represents the source for Scala classes for a given version.  The reason both a ClassLoader and the jars are required
* is that the compiler requires the location of the library/compiler jars on the (boot)classpath and the loader is used
* for the compiler itself.
* The 'version' field is the version used to obtain the Scala classes.  This is typically the version for the maven repository.
* The 'actualVersion' field should be used to uniquely identify the compiler.  It is obtained from the compiler.properties file.*/
final class ScalaInstance(val version: String, val loader: ClassLoader, val libraryJar: File, val compilerJar: File, val extraJars: Seq[File], val explicitActual: Option[String])
{
	require(version.indexOf(' ') < 0, "Version cannot contain spaces (was '" + version + "')")
	def jars = libraryJar :: compilerJar :: extraJars.toList
	/** Gets the version of Scala in the compiler.properties file from the loader.  This version may be different than that given by 'version'*/
	lazy val actualVersion = explicitActual getOrElse ScalaInstance.actualVersion(loader)("\n    version " + version + ", " + jarStrings)
	def jarStrings = "library jar: " + libraryJar + ", compiler jar: " + compilerJar
	override def toString = "Scala instance{version label " + version + ", actual version " + actualVersion + ", " + jarStrings + "}"
}
object ScalaInstance
{
	val ScalaOrg = "org.scala-lang"
	val VersionPrefix = "version "
	
	def apply(org: String, version: String, launcher: xsbti.Launcher): ScalaInstance =
	  // Due to incompatibility with previous launchers if scalaOrg has default value revert to an existing method
		if (org == ScalaOrg)
			apply(version, launcher)
		else try {
			apply(version, launcher.getScala(version, "", org))
		} catch {
			case x: NoSuchMethodError => error("Incompatible version of the xsbti.Launcher interface. Use sbt-0.12.x launcher instead.")
		}

	/** Creates a ScalaInstance using the given provider to obtain the jars and loader.*/
	def apply(version: String, launcher: xsbti.Launcher): ScalaInstance =
		apply(version, launcher.getScala(version))
	def apply(version: String, provider: xsbti.ScalaProvider): ScalaInstance =
		new ScalaInstance(version, provider.loader, provider.libraryJar, provider.compilerJar, (provider.jars.toSet - provider.libraryJar - provider.compilerJar).toSeq, None)

	def apply(scalaHome: File, launcher: xsbti.Launcher): ScalaInstance =
		apply(libraryJar(scalaHome), compilerJar(scalaHome), launcher, extraJars(scalaHome): _*)
	def apply(version: String, scalaHome: File, launcher: xsbti.Launcher): ScalaInstance =
		apply(version, libraryJar(scalaHome), compilerJar(scalaHome), launcher, extraJars(scalaHome) : _*)
	def apply(libraryJar: File, compilerJar: File, launcher: xsbti.Launcher, extraJars: File*): ScalaInstance =
	{
		val loader = scalaLoader(launcher, libraryJar :: compilerJar :: extraJars.toList)
		val version = actualVersion(loader)(" (library jar  " + libraryJar.getAbsolutePath + ")")
		new ScalaInstance(version, loader, libraryJar, compilerJar, extraJars, None)
	}
	def apply(version: String, libraryJar: File, compilerJar: File, launcher: xsbti.Launcher, extraJars: File*): ScalaInstance =
		apply(version, None, libraryJar, compilerJar, launcher, extraJars : _*)
	def apply(version: String, explicitActual: Option[String], libraryJar: File, compilerJar: File, launcher: xsbti.Launcher, extraJars: File*): ScalaInstance =
		new ScalaInstance(version, scalaLoader(launcher, libraryJar :: compilerJar :: extraJars.toList), libraryJar, compilerJar, extraJars, explicitActual)

	def extraJars(scalaHome: File): Seq[File] =
		optScalaJar(scalaHome, "jline.jar") ++ optScalaJar(scalaHome, "fjbg.jar")
		
	private def compilerJar(scalaHome: File) = scalaJar(scalaHome, "scala-compiler.jar")
	private def libraryJar(scalaHome: File) = scalaJar(scalaHome, "scala-library.jar")

	def scalaJar(scalaHome: File, name: String)  =  new File(scalaHome, "lib" + File.separator + name)
	def optScalaJar(scalaHome: File, name: String): List[File] =
	{
		val jar = scalaJar(scalaHome, name)
		if(jar.isFile) jar :: Nil else Nil
	}

	/** Gets the version of Scala in the compiler.properties file from the loader.*/
	private def actualVersion(scalaLoader: ClassLoader)(label: String) =
		try fastActualVersion(scalaLoader)
		catch { case e: Exception => slowActualVersion(scalaLoader)(label) }
	private def slowActualVersion(scalaLoader: ClassLoader)(label: String) =
	{
		val v = try { Class.forName("scala.tools.nsc.Properties", true, scalaLoader).getMethod("versionString").invoke(null).toString }
		catch { case cause: Exception => throw new InvalidScalaInstance("Scala instance doesn't exist or is invalid: " + label, cause) }
		if(v.startsWith(VersionPrefix)) v.substring(VersionPrefix.length) else v
	}
	private def fastActualVersion(scalaLoader: ClassLoader): String =
	{
		val stream = scalaLoader.getResourceAsStream("compiler.properties")
		try { 
			val props = new java.util.Properties
			props.load(stream)
			props.getProperty("version.number")
		}
		finally stream.close()
	}

	import java.net.{URL, URLClassLoader}
	private def scalaLoader(launcher: xsbti.Launcher, jars: Seq[File]): ClassLoader =
		new URLClassLoader(jars.map(_.toURI.toURL).toArray[URL], launcher.topLoader)
}
class InvalidScalaInstance(message: String, cause: Throwable) extends RuntimeException(message, cause)