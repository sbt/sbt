package xsbt

import java.io.File

object CacheTest// extends Properties("Cache test")
{
	import Task._
	import Cache._
	import FileInfo.hash._
	def checkFormattable(file: File)
	{
		val createTask = Task { new File("test") }
		val lengthTask = createTask map { f => println("File length: " + f.length); f.length }
		val cached = Cache(lengthTask, new File("/tmp/length-cache"))

		val cTask = (createTask :: cached :: TNil) map { case (file :: len :: HNil) => println("File: " + file + " length: " + len); len :: file :: HNil }
		val cachedC = Cache(cTask, new File("/tmp/c-cache"))

		try { TaskRunner(cachedC) }
		catch { case TasksFailed(failures) => failures.foreach(_.exception.printStackTrace) }
	}
}