/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package xsbt.boot

	import scala.collection.immutable.List
	import java.io.{File, FileFilter}
	import java.net.{URL, URLClassLoader}

object Pre
{
	def readLine(prompt: String): Option[String] = {
		val c = System.console()
		if(c eq null) None else Option(c.readLine(prompt))
	}
	def trimLeading(line: String) =
	{
		def newStart(i: Int): Int = if(i >= line.length || !Character.isWhitespace(line.charAt(i))) i else newStart(i+1)
		line.substring(newStart(0))
	}
	def isEmpty(line: String) = line.length == 0
	def isNonEmpty(line: String) = line.length > 0
	def assert(condition: Boolean, msg: => String): Unit = if (!condition) throw new AssertionError(msg)
	def assert(condition: Boolean): Unit = assert(condition, "Assertion failed")
	def require(condition: Boolean, msg: => String): Unit = if (!condition) throw new IllegalArgumentException(msg)
	def error(msg: String): Nothing = throw new BootException(prefixError(msg))
	def declined(msg: String): Nothing = throw new BootException(msg)
	def prefixError(msg: String): String = "Error during sbt execution: " + msg
	def toBoolean(s: String) = java.lang.Boolean.parseBoolean(s)
	def toArray[T : ClassManifest](list: List[T]) =
	{
		val arr = new Array[T](list.length)
		def copy(i: Int, rem: List[T]): Unit =
			if(i < arr.length)
			{
				arr(i) = rem.head
				copy(i+1, rem.tail)
			}
		copy(0, list)
		arr
	}
	/* These exist in order to avoid bringing in dependencies on RichInt and ArrayBuffer, among others. */
	def concat(a: Array[File], b: Array[File]): Array[File] =
	{
		val n = new Array[File](a.length + b.length)
		java.lang.System.arraycopy(a, 0, n, 0, a.length)
		java.lang.System.arraycopy(b, 0, n, a.length, b.length)
		n
	}
	def array(files: File*): Array[File] = toArray(files.toList)
	/* Saves creating a closure for default if it has already been evaluated*/
	def orElse[T](opt: Option[T], default: T) = if(opt.isDefined) opt.get else default

	def wrapNull(a: Array[File]): Array[File] = if(a == null) new Array[File](0) else a
	def const[B](b: B): Any => B = _ => b
	def strictOr[T](a: Option[T], b: Option[T]): Option[T] = a match { case None => b; case _ => a }
	def getOrError[T](a: Option[T], msg: String): T = a match { case None => error(msg); case Some(x) => x }
	def orNull[T >: Null](t: Option[T]): T = t match { case None => null; case Some(x) => x }

	def getJars(directories: List[File]): Array[File] = toArray(directories.flatMap(directory => wrapNull(directory.listFiles(JarFilter))))

	object JarFilter extends FileFilter
	{
		def accept(file: File) = !file.isDirectory && file.getName.endsWith(".jar")
	}
	def getMissing(loader: ClassLoader, classes: Iterable[String]): Iterable[String] =
	{
		def classMissing(c: String) = try { Class.forName(c, false, loader); false } catch { case e: ClassNotFoundException => true }
		classes.toList.filter(classMissing)
	}
	def toURLs(files: Array[File]): Array[URL] = files.map(_.toURI.toURL)

	def delete(f: File)
	{
		if(f.isDirectory)
		{
			val fs = f.listFiles()
			if(fs ne null) fs foreach delete
		}
		if(f.exists) f.delete()
	}
}
