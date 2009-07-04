/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package sbt

import java.lang.{Process => JProcess, ProcessBuilder => JProcessBuilder}
import java.io.{Closeable, File, IOException}
import java.io.{BufferedReader, InputStream, InputStreamReader, OutputStream, PipedInputStream, PipedOutputStream}
import java.net.URL

/** Methods for constructing simple commands that can then be combined. */
object Process
{
	implicit def apply(command: String): ProcessBuilder = apply(command.split("""\s+""")) // TODO: use CommandParser
	implicit def apply(command: Seq[String]): ProcessBuilder = apply(new JProcessBuilder(command.toArray : _*))
	def apply(command: String, arguments: Seq[String]): ProcessBuilder = apply(new JProcessBuilder((command :: arguments.toList).toArray : _*))
	implicit def apply(builder: JProcessBuilder): ProcessBuilder = new SimpleProcessBuilder(builder)
	implicit def apply(file: File): FilePartialBuilder = new FileBuilder(file)
	implicit def apply(url: URL): URLPartialBuilder = new URLBuilder(url)
	implicit def apply(command: scala.xml.Elem): ProcessBuilder = apply(command.text)
}

trait URLPartialBuilder extends NotNull
{
	def #>(b: ProcessBuilder): ProcessBuilder
	def #>>(b: File): ProcessBuilder
	def #>(b: File): ProcessBuilder
}
trait FilePartialBuilder extends NotNull
{
	def #>(b: ProcessBuilder): ProcessBuilder
	def #<(b: ProcessBuilder): ProcessBuilder
	def #<(url: URL): ProcessBuilder
	def #>>(b: File): ProcessBuilder
	def #>(b: File): ProcessBuilder
	def #<(file: File): ProcessBuilder
	def #<<(file: File): ProcessBuilder
}

/** Represents a process that is running or has finished running.
* It may be a compound process with several underlying native processes (such as 'a #&& b`).*/
trait Process extends NotNull
{
	/** Blocks until this process exits and returns the exit code.*/
	def exitValue(): Int
	/** Destroys this process. */
	def destroy(): Unit
}
/** Represents a runnable process. */
trait ProcessBuilder extends NotNull
{
	/** Starts the process represented by this builder, blocks until it exits, and returns the exit code.  Standard output and error are
	* sent to the console.*/
	def ! : Int
	/** Starts the process represented by this builder, blocks until it exits, and returns the exit code.  Standard output and error are
	* sent to the given Logger.*/
	def !(log: Logger): Int
	/** Starts the process represented by this builder.  Standard output and error are sent to the console.*/
	def run(): Process
	/** Starts the process represented by this builder.  Standard output and error are sent to the given Logger.*/
	def run(log: Logger): Process
	/** Starts the process represented by this builder.  I/O is handled by the given ProcessIO instance.*/
	def run(io: ProcessIO): Process

	/** Constructs a command that runs this command first and then `other` if this command succeeds.*/
	def #&& (other: ProcessBuilder): ProcessBuilder
	/** Constructs a command that runs this command first and then `other` if this command does not succeed.*/
	def #|| (other: ProcessBuilder): ProcessBuilder
	/** Constructs a command that will run this command and pipes the output to `other`.  `other` must be a simple command.*/
	def #| (other: ProcessBuilder): ProcessBuilder
	/** Constructs a command that will run this command and then `other`.  The exit code will be the exit code of `other`.*/
	def ## (other: ProcessBuilder): ProcessBuilder
	/** Reads the given file into the input stream of this process. */
	def #< (f: File): ProcessBuilder
	/** Reads the given URL into the input stream of this process. */
	def #< (f: URL): ProcessBuilder
	/** Writes the output stream of this process to the given file. */
	def #> (f: File): ProcessBuilder
	/** Appends the output stream of this process to the given file. */
	def #>> (f: File): ProcessBuilder

	def canPipeTo: Boolean
}
/** Each method will be called in a separate thread.*/
final class ProcessIO(val writeInput: OutputStream => Unit, val processOutput: InputStream => Unit, val processError: InputStream => Unit) extends NotNull
{
	def withOutput(process: InputStream => Unit): ProcessIO = new ProcessIO(writeInput, process, processError)
	def withError(process: InputStream => Unit): ProcessIO = new ProcessIO(writeInput, processOutput, process)
	def withInput(write: OutputStream => Unit): ProcessIO = new ProcessIO(write, processOutput, processError)
}