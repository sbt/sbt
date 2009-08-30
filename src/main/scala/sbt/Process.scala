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
	implicit def apply(command: scala.xml.Elem): ProcessBuilder = apply(command.text.trim)
	def apply(value: Boolean): ProcessBuilder = apply(value.toString, if(value) 0 else 1)
	def apply(name: String, exitValue: => Int): ProcessBuilder = new DummyProcessBuilder(name, exitValue)
	
	def cat(file: SourcePartialBuilder, files: SourcePartialBuilder*): ProcessBuilder = cat(file :: files.toList)
	def cat(files: Seq[SourcePartialBuilder]): ProcessBuilder =
	{
		require(!files.isEmpty)
		files.map(_.cat).reduceLeft(_ #&& _)
	}
}

trait SourcePartialBuilder extends NotNull
{
	/** Writes the output stream of this process to the given file. */
	def #> (f: File): ProcessBuilder = toFile(f, false)
	/** Appends the output stream of this process to the given file. */
	def #>> (f: File): ProcessBuilder = toFile(f, true)
	/** Writes the output stream of this process to the given OutputStream. The
	* argument is call-by-name, so the stream is recreated, written, and closed each
	* time this process is executed. */
	def #>(out: => OutputStream): ProcessBuilder = #> (new OutputStreamBuilder(out))
	def #>(b: ProcessBuilder): ProcessBuilder = new PipedProcessBuilder(toSource, b, false)
	private def toFile(f: File, append: Boolean) = #> (new FileOutput(f, append))
	def cat = toSource
	protected def toSource: ProcessBuilder
}
trait SinkPartialBuilder extends NotNull
{
	/** Reads the given file into the input stream of this process. */
	def #< (f: File): ProcessBuilder = #< (new FileInput(f))
	/** Reads the given URL into the input stream of this process. */
	def #< (f: URL): ProcessBuilder = #< (new URLInput(f))
	/** Reads the given InputStream into the input stream of this process. The
	* argument is call-by-name, so the stream is recreated, read, and closed each
	* time this process is executed. */
	def #<(in: => InputStream): ProcessBuilder = #< (new InputStreamBuilder(in))
	def #<(b: ProcessBuilder): ProcessBuilder = new PipedProcessBuilder(b, toSink, false)
	protected def toSink: ProcessBuilder
}

trait URLPartialBuilder extends SourcePartialBuilder
trait FilePartialBuilder extends SinkPartialBuilder with SourcePartialBuilder
{
	def #<<(f: File): ProcessBuilder
	def #<<(u: URL): ProcessBuilder
	def #<<(i: => InputStream): ProcessBuilder
	def #<<(p: ProcessBuilder): ProcessBuilder
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
trait ProcessBuilder extends SourcePartialBuilder with SinkPartialBuilder
{
	/** Starts the process represented by this builder, blocks until it exits, and returns the exit code.  Standard output and error are
	* sent to the console.*/
	def ! : Int
	/** Starts the process represented by this builder, blocks until it exits, and returns the exit code.  Standard output and error are
	* sent to the given Logger.*/
	def !(log: Logger): Int
	/** Starts the process represented by this builder, blocks until it exits, and returns the exit code.  Standard output and error are
	* sent to the console.  The newly started process reads from standard input of the current process if `connectInput` is true.*/
	def !< : Int
	/** Starts the process represented by this builder, blocks until it exits, and returns the exit code.  Standard output and error are
	* sent to the given Logger.  The newly started process reads from standard input of the current process if `connectInput` is true.*/
	def !<(log: Logger) : Int
	/** Starts the process represented by this builder.  Standard output and error are sent to the console.*/
	def run(): Process
	/** Starts the process represented by this builder.  Standard output and error are sent to the given Logger.*/
	def run(log: Logger): Process
	/** Starts the process represented by this builder.  I/O is handled by the given ProcessIO instance.*/
	def run(io: ProcessIO): Process
	/** Starts the process represented by this builder.  Standard output and error are sent to the console.
	* The newly started process reads from standard input of the current process if `connectInput` is true.*/
	def run(connectInput: Boolean): Process
	/** Starts the process represented by this builder, blocks until it exits, and returns the exit code.  Standard output and error are
	* sent to the given Logger.
	* The newly started process reads from standard input of the current process if `connectInput` is true.*/
	def run(log: Logger, connectInput: Boolean): Process

	/** Constructs a command that runs this command first and then `other` if this command succeeds.*/
	def #&& (other: ProcessBuilder): ProcessBuilder
	/** Constructs a command that runs this command first and then `other` if this command does not succeed.*/
	def #|| (other: ProcessBuilder): ProcessBuilder
	/** Constructs a command that will run this command and pipes the output to `other`.  `other` must be a simple command.*/
	def #| (other: ProcessBuilder): ProcessBuilder
	/** Constructs a command that will run this command and then `other`.  The exit code will be the exit code of `other`.*/
	def ## (other: ProcessBuilder): ProcessBuilder

	def canPipeTo: Boolean
}
/** Each method will be called in a separate thread.*/
final class ProcessIO(val writeInput: OutputStream => Unit, val processOutput: InputStream => Unit, val processError: InputStream => Unit) extends NotNull
{
	def withOutput(process: InputStream => Unit): ProcessIO = new ProcessIO(writeInput, process, processError)
	def withError(process: InputStream => Unit): ProcessIO = new ProcessIO(writeInput, processOutput, process)
	def withInput(write: OutputStream => Unit): ProcessIO = new ProcessIO(write, processOutput, processError)
}