/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package std

import java.io.{InputStream, IOException, OutputStream, Reader, Writer}
import java.io.{BufferedInputStream, BufferedOutputStream, BufferedReader, BufferedWriter, PrintWriter}
import java.io.{Closeable, File, FileInputStream, FileOutputStream, InputStreamReader, OutputStreamWriter}

import Path._

// no longer specific to Tasks, so 'TaskStreams' should be renamed
sealed trait TaskStreams[Key]
{
	def default = outID
	def outID = "out"
	def errorID = "err"
	
	def readText(key: Key, sid: String = default): BufferedReader
	def readBinary(a: Key, sid: String = default): BufferedInputStream
	
	final def readText(a: Key, sid: Option[String]): BufferedReader  =  readText(a, getID(sid))
	final def readBinary(a: Key, sid: Option[String]): BufferedInputStream  =  readBinary(a, getID(sid))

	def key: Key
	def text(sid: String = default): PrintWriter
	def binary(sid: String = default): BufferedOutputStream

	// default logger
	final lazy val log: Logger = log(default)
	def log(sid: String): Logger
	
	private[this] def getID(s: Option[String]) = s getOrElse default
}
sealed trait ManagedStreams[Key] extends TaskStreams[Key]
{
	def open()
	def close()
}

trait Streams[Key]
{
	def apply(a: Key): ManagedStreams[Key]
	def use[T](key: Key)(f: TaskStreams[Key] => T): T =
	{
		val s = apply(key)
		s.open()
		try { f(s) } finally { s.close() }
	}
}
trait CloseableStreams[Key] extends Streams[Key] with java.io.Closeable
object Streams
{
	private[this] val closeQuietly = (c: Closeable) => try { c.close() } catch { case _: IOException => () }

	def closeable[Key](delegate: Streams[Key]): CloseableStreams[Key] = new CloseableStreams[Key] {
		private[this] val streams = new collection.mutable.HashMap[Key,ManagedStreams[Key]]

		def apply(key: Key): ManagedStreams[Key] =
			synchronized { streams.getOrElseUpdate(key, delegate(key)) }

		def close(): Unit =
			synchronized { streams.values.foreach(_.close() ); streams.clear() }
	}
	
	def apply[Key](taskDirectory: Key => File, name: Key => String, mkLogger: (Key, PrintWriter) => Logger): Streams[Key] = new Streams[Key] {
	
		def apply(a: Key): ManagedStreams[Key] = new ManagedStreams[Key] {
			private[this] var opened: List[Closeable] = Nil
			private[this] var closed = false
		
			def readText(a: Key, sid: String = default): BufferedReader =
				make(a, sid)(f => new BufferedReader(new InputStreamReader(new FileInputStream(f), IO.defaultCharset)) )
				
			def readBinary(a: Key, sid: String = default): BufferedInputStream =
				make(a, sid)(f => new BufferedInputStream(new FileInputStream(f)))
	
			def text(sid: String = default): PrintWriter =
				make(a, sid)(f => new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), IO.defaultCharset))) )

			def binary(sid: String = default): BufferedOutputStream =
				make(a, sid)(f => new BufferedOutputStream(new FileOutputStream(f)))

			def log(sid: String): Logger = mkLogger(a, text(sid))

			def make[T <: Closeable](a: Key, sid: String)(f: File => T): T = synchronized {
				checkOpen()
				val file = taskDirectory(a) / sid
				IO.touch(file, false)
				val t = f( file )
				opened ::= t
				t
			}

			def key: Key = a
			def open() {}
			
			def close(): Unit = synchronized {
				if(!closed)
				{
					closed = true
					opened foreach closeQuietly
				}
			}
			def checkOpen(): Unit = synchronized {
				if(closed) error("Streams for '" + name(a) + "' have been closed.")
			}
		}
	}
}