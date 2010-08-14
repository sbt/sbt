/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package std

import java.io.{InputStream, OutputStream, Reader, Writer}
import java.io.{BufferedInputStream, BufferedOutputStream, BufferedReader, BufferedWriter, PrintWriter}
import java.io.{Closeable, File, FileInputStream, FileOutputStream, InputStreamReader, OutputStreamWriter}

import Path._

sealed trait TaskStreams
{
	def default = outID
	def outID = "out"
	def errorID = "err"
	
	def readText(a: Task[_], sid: String = default, update: Boolean = true): Task[BufferedReader]
	def readBinary(a: Task[_], sid: String = default, update: Boolean = true): Task[BufferedInputStream]
	
	final def readText(a: Task[_], sid: Option[String], update: Boolean): Task[BufferedReader] =
		readText(a, getID(sid), update)
		
	final def readBinary(a: Task[_], sid: Option[String], update: Boolean): Task[BufferedInputStream] =
		readBinary(a, getID(sid), update)

	def text(sid: String = default): PrintWriter
	def binary(sid: String = default): BufferedOutputStream

	// default logger
	/*val log: Logger
	def log(sid: String): Logger*/
	
	private[this] def getID(s: Option[String]) = s getOrElse default
}
private[sbt] sealed trait ManagedTaskStreams extends TaskStreams
{
	def open()
	def close()
}

sealed trait Streams
{
	def apply(a: Task[_], update: Boolean = true): ManagedTaskStreams
}
object Streams
{
	private[this] val closeQuietly = (_: Closeable).close()
	
	def multi[Owner](bases: Owner => File, taskOwner: Task[_] => Option[Owner]): Streams =
	{
		val taskDirectory = (t: Task[_]) => taskOwner(t) map bases getOrElse error("Cannot get streams for task '" + name(t) + "' with no owner.")
		apply(taskDirectory)
	}
	
	def apply(taskDirectory: Task[_] => File): Streams = new Streams { streams =>
	
		def apply(a: Task[_], update: Boolean): ManagedTaskStreams = new ManagedTaskStreams {
			private[this] var opened: List[Closeable] = Nil
			private[this] var closed = false
		
			def readText(a: Task[_], sid: String = default, update: Boolean = true): Task[BufferedReader] =
				maybeUpdate(a, readText0(a, sid), update)
				
			def readBinary(a: Task[_], sid: String = default, update: Boolean = true): Task[BufferedInputStream] =
				maybeUpdate(a, readBinary0(a, sid), update)
	
			def text(sid: String = default): PrintWriter =
				make(a, sid)(f => new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), IO.defaultCharset))) )

			def binary(sid: String = default): BufferedOutputStream =
				make(a, sid)(f => new BufferedOutputStream(new FileOutputStream(f)))

			def make[T <: Closeable](a: Task[_], sid: String)(f: File => T): T = synchronized {
				checkOpen()
				val file = taskDirectory(a) / sid
				IO.touch(file)
				val t = f( file )
				opened ::= t
				t
			}
			
			def readText0(a: Task[_], sid: String): BufferedReader =
				make(a, sid)(f => new BufferedReader(new InputStreamReader(new FileInputStream(f), IO.defaultCharset)) )
			
			def readBinary0(a: Task[_], sid: String): BufferedInputStream =
				make(a, sid)(f => new BufferedInputStream(new FileInputStream(f)))
			
			def maybeUpdate[T](base: Task[_], result: => T, update: Boolean) =
			{
				def basic(a: Action[T]) = Task(Info(), a)
				val main = Pure(result _)
				val act = if(update) DependsOn(basic(main),  base :: Nil) else main
				basic(act)
			}
			
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
	
	def name(a: Task[_]): String = a.info.name getOrElse anonName(a)
	def anonName(a: Task[_]) = "anon" + java.lang.Integer.toString(java.lang.System.identityHashCode(a), 36)
}