/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package xsbt.api

	import xsbti.api._
	import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, ObjectInputStream, ObjectOutputStream, OutputStream}

object APIFormat
{
	def write(api: Source): Array[Byte] =
	{
		val baos = new ByteArrayOutputStream
		write(api, baos)
		baos.toByteArray
	}
	def write(api: Source, out: OutputStream)
	{
		val objOut = new ObjectOutputStream(out)
		try { objOut.writeObject(api) }
		finally { objOut.close() }
	}
	def read(bytes: Array[Byte]): Source = read(new ByteArrayInputStream(bytes))
	def read(in: InputStream): Source =
	{
		try
		{
			val objIn = new ObjectInputStream(in)
			try { objIn.readObject().asInstanceOf[Source] }
			finally { objIn.close() }
		}
		catch { case _: java.io.EOFException | _: java.io.InvalidClassException => emptySource}
	}
	def emptySource: Source = new Source(Array(), Array())
}