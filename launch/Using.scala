/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package xsbt.boot

import java.io.{Closeable, File, FileInputStream, FileOutputStream, InputStream, OutputStream}

object Using
{
	def apply[R <: Closeable,T](create: R)(f: R => T): T = withResource(create)(f)
	def withResource[R <: Closeable,T](r: R)(f: R => T): T = try { f(r) } finally { r.close() }
}

object Copy
{
	def apply(files: List[File], toDirectory: File): Unit =  files.foreach(file => apply(file, toDirectory))
	def apply(file: File, toDirectory: File)
	{
		toDirectory.mkdirs()
		Using(new FileInputStream(file)) { in =>
			Using(new FileOutputStream(new File(toDirectory, file.getName))) { out =>
				transfer(in, out)
			}
		}
	}
	def transfer(in: InputStream, out: OutputStream)
	{
		val buffer = new Array[Byte](8192)
		def next()
		{
			val read = in.read(buffer)
			if(read > 0)
			{
				out.write(buffer, 0, read)
				next()
			}
		}
		next()
	}
}