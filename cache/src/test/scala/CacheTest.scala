package xsbt

import java.io.File

object CacheTest// extends Properties("Cache test")
{
	val lengthCache = new File("/tmp/length-cache")
	val cCache = new File("/tmp/c-cache")

	import Task._
	import Cache._
	import FileInfo.hash._
	def test
	{
		val createTask = Task { new File("test") }

		val length = (f: File) => { println("File length: " + f.length); f.length }
		val cachedLength = cached(lengthCache) ( length )

		val lengthTask = createTask map cachedLength

		val c = (file: File, len: Long) => { println("File: " + file + ", length: " + len); len :: file :: HNil }
		val cTask = (createTask :: lengthTask :: TNil) map cached(cCache) { case (file :: len :: HNil) => c(file, len) }

		try { TaskRunner(cTask) }
		catch { case TasksFailed(failures) => failures.foreach(_.exception.printStackTrace) }
	}
}