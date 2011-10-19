/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package sbt

import java.lang.{Process => JProcess, ProcessBuilder => JProcessBuilder}
import java.io.{Closeable, File, IOException}
import java.io.{BufferedReader, InputStream, InputStreamReader, OutputStream, PipedInputStream, PipedOutputStream}
import java.net.URL

trait ProcessExtra
{
	import Process._
	implicit def builderToProcess(builder: JProcessBuilder): ProcessBuilder = apply(builder)
	implicit def fileToProcess(file: File): FilePartialBuilder = apply(file)
	implicit def urlToProcess(url: URL): URLPartialBuilder = apply(url)
	implicit def xmlToProcess(command: scala.xml.Elem): ProcessBuilder = apply(command)
	implicit def buildersToProcess[T](builders: Seq[T])(implicit convert: T => SourcePartialBuilder): Seq[SourcePartialBuilder] = applySeq(builders)

	implicit def stringToProcess(command: String): ProcessBuilder = apply(command)
	implicit def stringSeqToProcess(command: Seq[String]): ProcessBuilder = apply(command)
}

/** Methods for constructing simple commands that can then be combined. */
object Process extends ProcessExtra
{
	def apply(command: String): ProcessBuilder = apply(command, None)

	def apply(command: Seq[String]): ProcessBuilder = apply (command.toArray, None)

	def apply(command: String, arguments: Seq[String]): ProcessBuilder = apply(command :: arguments.toList, None)
	/** create ProcessBuilder with working dir set to File and extra environment variables */
	def apply(command: String, cwd: File, extraEnv: (String,String)*): ProcessBuilder =
		apply(command, Some(cwd), extraEnv : _*)
	/** create ProcessBuilder with working dir set to File and extra environment variables */
	def apply(command: Seq[String], cwd: File, extraEnv: (String,String)*): ProcessBuilder =
		apply(command, Some(cwd), extraEnv : _*)
	/** create ProcessBuilder with working dir optionaly set to File and extra environment variables */
	def apply(command: String, cwd: Option[File], extraEnv: (String,String)*): ProcessBuilder = {
		apply(command.split("""\s+"""), cwd, extraEnv : _*)
		// not smart to use this on windows, because CommandParser uses \ to escape ".
		/*CommandParser.parse(command) match {
			case Left(errorMsg) => error(errorMsg)
			case Right((cmd, args)) => apply(cmd :: args, cwd, extraEnv : _*)
		}*/
	}
	/** create ProcessBuilder with working dir optionaly set to File and extra environment variables */
	def apply(command: Seq[String], cwd: Option[File], extraEnv: (String,String)*): ProcessBuilder = {
		val jpb = new JProcessBuilder(command.toArray : _*)
		cwd.foreach(jpb directory _)
		extraEnv.foreach { case (k, v) => jpb.environment.put(k, v) }
		apply(jpb)
	}
	def apply(builder: JProcessBuilder): ProcessBuilder = new SimpleProcessBuilder(builder)
	def apply(file: File): FilePartialBuilder = new FileBuilder(file)
	def apply(url: URL): URLPartialBuilder = new URLBuilder(url)
	def apply(command: scala.xml.Elem): ProcessBuilder = apply(command.text.trim)
	def applySeq[T](builders: Seq[T])(implicit convert: T => SourcePartialBuilder): Seq[SourcePartialBuilder] = builders.map(convert)

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
	/** Starts the process represented by this builder, blocks until it exits, and returns the output as a String.  Standard error is
	* sent to the console.  If the exit code is non-zero, an exception is thrown.*/
	def !! : String
	/** Starts the process represented by this builder, blocks until it exits, and returns the output as a String.  Standard error is
	* sent to the provided ProcessLogger.  If the exit code is non-zero, an exception is thrown.*/
	def !!(log: ProcessLogger) : String
	/** Starts the process represented by this builder.  The output is returned as a Stream that blocks when lines are not available
	* but the process has not completed.  Standard error is sent to the console.  If the process exits with a non-zero value,
	* the Stream will provide all lines up to termination and then throw an exception. */
	def lines: Stream[String]
	/** Starts the process represented by this builder.  The output is returned as a Stream that blocks when lines are not available
	* but the process has not completed.  Standard error is sent to the provided ProcessLogger.  If the process exits with a non-zero value,
	* the Stream will provide all lines up to termination but will not throw an exception. */
	def lines(log: ProcessLogger): Stream[String]
	/** Starts the process represented by this builder.  The output is returned as a Stream that blocks when lines are not available
	* but the process has not completed.  Standard error is sent to the console. If the process exits with a non-zero value,
	* the Stream will provide all lines up to termination but will not throw an exception. */
	def lines_! : Stream[String]
	/** Starts the process represented by this builder.  The output is returned as a Stream that blocks when lines are not available
	* but the process has not completed.  Standard error is sent to the provided ProcessLogger. If the process exits with a non-zero value,
	* the Stream will provide all lines up to termination but will not throw an exception. */
	def lines_!(log: ProcessLogger): Stream[String]
	/** Starts the process represented by this builder, blocks until it exits, and returns the exit code.  Standard output and error are
	* sent to the console.*/
	def ! : Int
	/** Starts the process represented by this builder, blocks until it exits, and returns the exit code.  Standard output and error are
	* sent to the given ProcessLogger.*/
	def !(log: ProcessLogger): Int
	/** Starts the process represented by this builder, blocks until it exits, and returns the exit code.  Standard output and error are
	* sent to the console.  The newly started process reads from standard input of the current process.*/
	def !< : Int
	/** Starts the process represented by this builder, blocks until it exits, and returns the exit code.  Standard output and error are
	* sent to the given ProcessLogger.  The newly started process reads from standard input of the current process.*/
	def !<(log: ProcessLogger) : Int
	/** Starts the process represented by this builder.  Standard output and error are sent to the console.*/
	def run(): Process
	/** Starts the process represented by this builder.  Standard output and error are sent to the given ProcessLogger.*/
	def run(log: ProcessLogger): Process
	/** Starts the process represented by this builder.  I/O is handled by the given ProcessIO instance.*/
	def run(io: ProcessIO): Process
	/** Starts the process represented by this builder.  Standard output and error are sent to the console.
	* The newly started process reads from standard input of the current process if `connectInput` is true.*/
	def run(connectInput: Boolean): Process
	/** Starts the process represented by this builder, blocks until it exits, and returns the exit code.  Standard output and error are
	* sent to the given ProcessLogger.
	* The newly started process reads from standard input of the current process if `connectInput` is true.*/
	def run(log: ProcessLogger, connectInput: Boolean): Process

	def runBuffered(log: ProcessLogger, connectInput: Boolean): Process

	/** Constructs a command that runs this command first and then `other` if this command succeeds.*/
	def #&& (other: ProcessBuilder): ProcessBuilder
	/** Constructs a command that runs this command first and then `other` if this command does not succeed.*/
	def #|| (other: ProcessBuilder): ProcessBuilder
	/** Constructs a command that will run this command and pipes the output to `other`.  `other` must be a simple command.*/
	def #| (other: ProcessBuilder): ProcessBuilder
	/** Constructs a command that will run this command and then `other`.  The exit code will be the exit code of `other`.*/
	def ### (other: ProcessBuilder): ProcessBuilder

	def canPipeTo: Boolean
}
/** Each method will be called in a separate thread.*/
final class ProcessIO(val writeInput: OutputStream => Unit, val processOutput: InputStream => Unit, val processError: InputStream => Unit) extends NotNull
{
	def withOutput(process: InputStream => Unit): ProcessIO = new ProcessIO(writeInput, process, processError)
	def withError(process: InputStream => Unit): ProcessIO = new ProcessIO(writeInput, processOutput, process)
	def withInput(write: OutputStream => Unit): ProcessIO = new ProcessIO(write, processOutput, processError)
}
trait ProcessLogger
{
	def info(s: => String): Unit
	def error(s: => String): Unit
	def buffer[T](f: => T): T
}