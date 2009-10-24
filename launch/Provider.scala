package xsbt.boot

import Pre._
import java.io.{File, FileFilter}
import java.net.{URL, URLClassLoader}
import java.util.concurrent.Callable

trait Provider extends NotNull
{
	def configuration: UpdateConfiguration
	def baseDirectories: List[File]
	def testLoadClasses: List[String]
	def target: UpdateTarget
	def failLabel: String
	def parentLoader: ClassLoader
	def lockFile: File

	val (jars, loader) = Locks(lockFile, new initialize)
	private final class initialize extends Callable[(Array[File], ClassLoader)]
	{
		def call =
		{
			val (existingJars, existingLoader) = createLoader
			if(Provider.needsUpdate(existingLoader, testLoadClasses))
			{
				( new Update(configuration) )(target)
				val (newJars, newLoader) = createLoader
				Provider.failIfMissing(newLoader, testLoadClasses, failLabel)
				(newJars, newLoader)
			}
			else
				(existingJars, existingLoader)
		}
		def createLoader =
		{
			val jars = Provider.getJars(baseDirectories)
			(jars, new URLClassLoader(jars.map(_.toURI.toURL), parentLoader) )
		}
	}
}

object Provider
{
	def getJars(directories: List[File]): Array[File] = toArray(directories.flatMap(directory => wrapNull(directory.listFiles(JarFilter))))
	def wrapNull(a: Array[File]): Array[File] = if(a == null) Array() else a

	object JarFilter extends FileFilter
	{
		def accept(file: File) = !file.isDirectory && file.getName.endsWith(".jar")
	}
	def failIfMissing(loader: ClassLoader, classes: Iterable[String], label: String) =
		checkTarget(loader, classes, (), missing => error("Could not retrieve " + label + ": missing " + missing.mkString(", ")))
	def needsUpdate(loader: ClassLoader, classes: Iterable[String]) = checkTarget(loader, classes, false, x => true)
	def checkTarget[T](loader: ClassLoader, classes: Iterable[String], ifSuccess: => T, ifFailure: Iterable[String] => T): T =
	{
		def classMissing(c: String) = try { Class.forName(c, false, loader); false } catch { case e: ClassNotFoundException => true }
		val missing = classes.toList.filter(classMissing)
		if(missing.isEmpty) ifSuccess else ifFailure(missing)
	}
}