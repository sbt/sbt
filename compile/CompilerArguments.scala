package xsbt

	import java.io.File

object CompilerArguments
{
	def apply(scalaLibDirectory: File)(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], compilerOnClasspath: Boolean): Seq[String] =
	{
		val scalaHome = System.getProperty("scala.home")
		assert((scalaHome eq null) || scalaHome.isEmpty, "'scala.home' should not be set (was " + scalaHome + ")")
		def abs(files: Set[File]) = files.map(_.getAbsolutePath)
		val originalBoot = System.getProperty("sun.boot.class.path", "")
		val newBootPrefix = if(originalBoot.isEmpty) "" else originalBoot + File.pathSeparator
		val bootClasspathOption = Seq("-bootclasspath", newBootPrefix + scalaLibraryJar(scalaLibDirectory).getAbsolutePath)
		val cp2 = classpath ++ (if(compilerOnClasspath) scalaCompilerJar(scalaLibDirectory):: Nil else Nil)
		val classpathOption = Seq("-cp", abs(cp2).mkString(File.pathSeparator) )
		val outputOption = Seq("-d", outputDirectory.getAbsolutePath)
		options ++ outputOption ++ bootClasspathOption ++ classpathOption ++ abs(sources)
	}
	private def scalaLibraryJar(scalaLibDirectory: File): File = new File(scalaLibDirectory, "scala-library.jar")
	private def scalaCompilerJar(scalaLibDirectory: File): File = new File(scalaLibDirectory, "scala-compiler.jar")
}