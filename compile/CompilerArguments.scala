package xsbt

	import java.io.File

/** Forms the list of options that is passed to the compiler from the required inputs and other options.
* The directory containing scala-library.jar and scala-compiler.jar (scalaLibDirectory) is required in
* order to add these jars to the boot classpath. The 'scala.home' property must be unset because Scala
* puts jars in that directory on the bootclasspath.  Because we use multiple Scala versions,
* this would lead to compiling against the wrong library jar.*/
class CompilerArguments(scalaInstance: ScalaInstance)
{
	def apply(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], compilerOnClasspath: Boolean): Seq[String] =
	{
		checkScalaHomeUnset()
		val bootClasspathOption = Seq("-bootclasspath", createBootClasspath)
		val cpWithCompiler = classpath ++ (if(compilerOnClasspath) scalaInstance.compilerJar :: Nil else Nil)
		val classpathOption = Seq("-cp", abs(cpWithCompiler).mkString(File.pathSeparator) )
		val outputOption = Seq("-d", outputDirectory.getAbsolutePath)
		options ++ outputOption ++ bootClasspathOption ++ classpathOption ++ abs(sources)
	}
	protected def abs(files: Set[File]) = files.map(_.getAbsolutePath)
	protected def checkScalaHomeUnset()
	{
		val scalaHome = System.getProperty("scala.home")
		assert((scalaHome eq null) || scalaHome.isEmpty, "'scala.home' should not be set (was " + scalaHome + ")")
	}
	/** Add the correct Scala library jar to the boot classpath.*/
	protected def createBootClasspath =
	{
		val originalBoot = System.getProperty("sun.boot.class.path", "")
		val newBootPrefix = if(originalBoot.isEmpty) "" else originalBoot + File.pathSeparator
		newBootPrefix + scalaInstance.libraryJar.getAbsolutePath
	}
}