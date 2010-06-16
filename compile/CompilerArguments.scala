package xsbt

	import java.io.File
	import CompilerArguments.{abs, absString}

/** Forms the list of options that is passed to the compiler from the required inputs and other options.
* The directory containing scala-library.jar and scala-compiler.jar (scalaLibDirectory) is required in
* order to add these jars to the boot classpath. The 'scala.home' property must be unset because Scala
* puts jars in that directory on the bootclasspath.  Because we use multiple Scala versions,
* this would lead to compiling against the wrong library jar.*/
class CompilerArguments(scalaInstance: ScalaInstance, cp: ClasspathOptions) extends NotNull
{
	def apply(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String]): Seq[String] =
	{
		checkScalaHomeUnset()
		val bootClasspathOption = if(cp.autoBoot) Seq("-bootclasspath", createBootClasspath) else Nil
		val cpWithCompiler = finishClasspath(classpath)
		val classpathOption = Seq("-cp", absString(cpWithCompiler) )
		val outputOption = Seq("-d", outputDirectory.getAbsolutePath)
		options ++ outputOption ++ bootClasspathOption ++ classpathOption ++ abs(sources)
	}
	def finishClasspath(classpath: Set[File]): Set[File] =
		classpath ++ include(cp.compiler, scalaInstance.compilerJar) ++ include(cp.extra, scalaInstance.extraJars : _*)
	private def include(flag: Boolean, jars: File*) = if(flag) jars else Nil
	protected def abs(files: Set[File]) = files.map(_.getAbsolutePath).toList.sortWith(_ < _)
	protected def checkScalaHomeUnset()
	{
		val scalaHome = System.getProperty("scala.home")
		assert((scalaHome eq null) || scalaHome.isEmpty, "'scala.home' should not be set (was " + scalaHome + ")")
	}
	/** Add the correct Scala library jar to the boot classpath.*/
	def createBootClasspath =
	{
		val originalBoot = System.getProperty("sun.boot.class.path", "")
		val newBootPrefix = if(originalBoot.isEmpty) "" else originalBoot + File.pathSeparator
		newBootPrefix + scalaInstance.libraryJar.getAbsolutePath
	}
}
class ClasspathOptions(val autoBoot: Boolean, val compiler: Boolean, val extra: Boolean)  extends NotNull
object ClasspathOptions
{
	def manual = new ClasspathOptions(false, false, false)
	def auto = new ClasspathOptions(true, true, true)
}
object CompilerArguments
{
	def abs(files: Seq[File]): Seq[String] = files.map(_.getAbsolutePath)
	def abs(files: Set[File]): Seq[String] = abs(files.toSeq)
	def absString(files: Seq[File]): String = abs(files).mkString(File.pathSeparator)
	def absString(files: Set[File]): String = absString(files.toSeq)
}