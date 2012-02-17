/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah, Vesa Vilhonen
 */
package sbt

import java.lang.{Process => JProcess, ProcessBuilder => JProcessBuilder}
import java.io.{BufferedReader, Closeable, InputStream, InputStreamReader, IOException, OutputStream, PrintStream}
import java.io.{FilterInputStream, FilterOutputStream, PipedInputStream, PipedOutputStream}
import java.io.{File, FileInputStream, FileOutputStream}
import java.net.URL

import scala.concurrent.SyncVar

/** Runs provided code in a new Thread and returns the Thread instance. */
private object Spawn
{
	def apply(f: => Unit): Thread = apply(f, false)
	def apply(f: => Unit, daemon: Boolean): Thread =
	{
		val thread = new Thread() { override def run() = { f } }
		thread.setDaemon(daemon)
		thread.start()
		thread
	}
}
private object Future
{
	def apply[T](f: => T): () => T =
	{
		val result = new SyncVar[Either[Throwable, T]]
		def run: Unit =
			try { result.set(Right(f)) }
			catch { case e: Exception => result.set(Left(e)) }
		Spawn(run)
		() =>
			result.get match
			{
				case Right(value) => value
				case Left(exception) => throw exception
			}
	}
}

object BasicIO
{
	def apply(buffer: StringBuffer, log: Option[ProcessLogger], withIn: Boolean) = new ProcessIO(input(withIn), processFully(buffer), getErr(log))
	def apply(log: ProcessLogger, withIn: Boolean) = new ProcessIO(input(withIn), processInfoFully(log), processErrFully(log))

	def getErr(log: Option[ProcessLogger]) = log match { case Some(lg) => processErrFully(lg); case None => toStdErr }

	private def processErrFully(log: ProcessLogger) = processFully(s => log.error(s))
	private def processInfoFully(log: ProcessLogger) = processFully(s => log.info(s))

	def ignoreOut = (i: OutputStream) => ()
	final val BufferSize = 8192
	final val Newline = System.getProperty("line.separator")

	def close(c: java.io.Closeable) = try { c.close() } catch { case _: java.io.IOException => () }
	def processFully(buffer: Appendable): InputStream => Unit = processFully(appendLine(buffer))
	def processFully(processLine: String => Unit): InputStream => Unit =
		in =>
		{
			val reader = new BufferedReader(new InputStreamReader(in))
			processLinesFully(processLine)(reader.readLine)
		}
	def processLinesFully(processLine: String => Unit)(readLine: () => String)
	{
		def readFully()
		{
			val line = readLine()
			if(line != null)
			{
				processLine(line)
				readFully()
			}
		}
		readFully()
	}
	def connectToIn(o: OutputStream) { transferFully(System.in, o) }
	def input(connect: Boolean): OutputStream => Unit = if(connect) connectToIn else ignoreOut
	def standard(connectInput: Boolean): ProcessIO = standard(input(connectInput))
	def standard(in: OutputStream => Unit): ProcessIO = new ProcessIO(in, toStdOut, toStdErr)

	def toStdErr = (in: InputStream) => transferFully(in, System.err)
	def toStdOut = (in: InputStream) => transferFully(in, System.out)

	def transferFully(in: InputStream, out: OutputStream): Unit =
		try { transferFullyImpl(in, out) }
		catch { case  _: InterruptedException => () }

	private[this] def appendLine(buffer: Appendable): String => Unit =
		line =>
		{
			buffer.append(line)
			buffer.append(Newline)
		}

	private[this] def transferFullyImpl(in: InputStream, out: OutputStream)
	{
		val continueCount = 1//if(in.isInstanceOf[PipedInputStream]) 1 else 0
		val buffer = new Array[Byte](BufferSize)
		def read
		{
			val byteCount = in.read(buffer)
			if(byteCount >= continueCount)
			{
				out.write(buffer, 0, byteCount)
				out.flush()
				read
			}
		}
		read
	}
}


private abstract class AbstractProcessBuilder extends ProcessBuilder with SinkPartialBuilder with SourcePartialBuilder
{
	def #&&(other: ProcessBuilder): ProcessBuilder = new AndProcessBuilder(this, other)
	def #||(other: ProcessBuilder): ProcessBuilder = new OrProcessBuilder(this, other)
	def #|(other: ProcessBuilder): ProcessBuilder =
	{
		require(other.canPipeTo, "Piping to multiple processes is not supported.")
		new PipedProcessBuilder(this, other, false)
	}
	def ###(other: ProcessBuilder): ProcessBuilder = new SequenceProcessBuilder(this, other)
	
	protected def toSource = this
	protected def toSink = this
	
	def run(): Process = run(false)
	def run(connectInput: Boolean): Process = run(BasicIO.standard(connectInput))
	def run(log: ProcessLogger): Process = run(log, false)
	def run(log: ProcessLogger, connectInput: Boolean): Process = run(BasicIO(log, connectInput))

	private[this] def getString(log: Option[ProcessLogger], withIn: Boolean): String =
	{
		val buffer = new StringBuffer
		val code = this ! BasicIO(buffer, log, withIn)
		if(code == 0) buffer.toString else error("Nonzero exit value: " + code)
	}
	def !! = getString(None, false)
	def !!(log: ProcessLogger) = getString(Some(log), false)
	def !!< = getString(None, true)
	def !!<(log: ProcessLogger) = getString(Some(log), true)

	def lines: Stream[String] = lines(false, true, None)
	def lines(log: ProcessLogger): Stream[String] = lines(false, true, Some(log))
	def lines_! : Stream[String] = lines(false, false, None)
	def lines_!(log: ProcessLogger): Stream[String] = lines(false, false, Some(log))

	private[this] def lines(withInput: Boolean, nonZeroException: Boolean, log: Option[ProcessLogger]): Stream[String] =
	{
		val streamed = Streamed[String](nonZeroException)
		val process = run(new ProcessIO(BasicIO.input(withInput), BasicIO.processFully(streamed.process), BasicIO.getErr(log)))
		Spawn { streamed.done(process.exitValue()) }
		streamed.stream()
	}

	def ! = run(false).exitValue()
	def !< = run(true).exitValue()
	def !(log: ProcessLogger) = runBuffered(log, false).exitValue()
	def !<(log: ProcessLogger) = runBuffered(log, true).exitValue()
	def runBuffered(log: ProcessLogger, connectInput: Boolean) =
		log.buffer {  run(log, connectInput) }
	def !(io: ProcessIO) = run(io).exitValue()

	def canPipeTo = false
}

private[sbt] class URLBuilder(url: URL) extends URLPartialBuilder with SourcePartialBuilder
{
	protected def toSource = new URLInput(url)
}
private[sbt] class FileBuilder(base: File) extends FilePartialBuilder with SinkPartialBuilder with SourcePartialBuilder
{
	protected def toSource = new FileInput(base)
	protected def toSink = new FileOutput(base, false)
	def #<<(f: File): ProcessBuilder = #<<(new FileInput(f))
	def #<<(u: URL): ProcessBuilder = #<<(new URLInput(u))
	def #<<(s: => InputStream): ProcessBuilder = #<<(new InputStreamBuilder(s))
	def #<<(b: ProcessBuilder): ProcessBuilder = new PipedProcessBuilder(b, new FileOutput(base, true), false)
}

private abstract class BasicBuilder extends AbstractProcessBuilder
{
	protected[this] def checkNotThis(a: ProcessBuilder) = require(a != this, "Compound process '" + a + "' cannot contain itself.")
	final def run(io: ProcessIO): Process =
	{
		val p = createProcess(io)
		p.start()
		p
	}
	protected[this] def createProcess(io: ProcessIO): BasicProcess
}
private abstract class BasicProcess extends Process
{
	def start(): Unit
}

private abstract class CompoundProcess extends BasicProcess
{
	def destroy() { destroyer() }
	def exitValue() = getExitValue().getOrElse(error("No exit code: process destroyed."))

	def start() = getExitValue
	
	protected lazy val (getExitValue, destroyer) =
	{
		val code = new SyncVar[Option[Int]]()
		code.set(None)
		val thread = Spawn(code.set(runAndExitValue()))
		
		(
			Future { thread.join(); code.get },
			() => thread.interrupt()
		)
	}
	
	/** Start and block until the exit value is available and then return it in Some.  Return None if destroyed (use 'run')*/
	protected[this] def runAndExitValue(): Option[Int]

	protected[this] def runInterruptible[T](action: => T)(destroyImpl: => Unit): Option[T] =
	{
		try { Some(action) }
		catch { case _: InterruptedException => destroyImpl; None }
	}
}

private abstract class SequentialProcessBuilder(a: ProcessBuilder, b: ProcessBuilder, operatorString: String) extends BasicBuilder
{
	checkNotThis(a)
	checkNotThis(b)
	override def toString = " ( " + a + " " + operatorString + " " + b + " ) "
}
private class PipedProcessBuilder(first: ProcessBuilder, second: ProcessBuilder, toError: Boolean) extends SequentialProcessBuilder(first, second, if(toError) "#|!" else "#|")
{
	override def createProcess(io: ProcessIO) = new PipedProcesses(first, second, io, toError)
}
private class AndProcessBuilder(first: ProcessBuilder, second: ProcessBuilder) extends SequentialProcessBuilder(first, second, "#&&")
{
	override def createProcess(io: ProcessIO) = new AndProcess(first, second, io)
}
private class OrProcessBuilder(first: ProcessBuilder, second: ProcessBuilder) extends SequentialProcessBuilder(first, second, "#||")
{
	override def createProcess(io: ProcessIO) = new OrProcess(first, second, io)
}
private class SequenceProcessBuilder(first: ProcessBuilder, second: ProcessBuilder) extends SequentialProcessBuilder(first, second, "###")
{
	override def createProcess(io: ProcessIO) = new ProcessSequence(first, second, io)
}

private class SequentialProcess(a: ProcessBuilder, b: ProcessBuilder, io: ProcessIO, evaluateSecondProcess: Int => Boolean) extends CompoundProcess
{
	protected[this] override def runAndExitValue() =
	{
		val first = a.run(io)
		runInterruptible(first.exitValue)(first.destroy()) flatMap
		{ codeA =>
			if(evaluateSecondProcess(codeA))
			{
				val second = b.run(io)
				runInterruptible(second.exitValue)(second.destroy())
			}
			else
				Some(codeA)
		}
	}
}
private class AndProcess(a: ProcessBuilder, b: ProcessBuilder, io: ProcessIO) extends SequentialProcess(a, b, io, _ == 0)
private class OrProcess(a: ProcessBuilder, b: ProcessBuilder, io: ProcessIO) extends SequentialProcess(a, b, io, _ != 0)
private class ProcessSequence(a: ProcessBuilder, b: ProcessBuilder, io: ProcessIO) extends SequentialProcess(a, b, io, ignore => true)


private class PipedProcesses(a: ProcessBuilder, b: ProcessBuilder, defaultIO: ProcessIO, toError: Boolean) extends CompoundProcess
{
	protected[this] override def runAndExitValue() =
	{
		val currentSource = new SyncVar[Option[InputStream]]
		val pipeOut = new PipedOutputStream
		val source = new PipeSource(currentSource, pipeOut, a.toString)
		source.start()
		
		val pipeIn = new PipedInputStream(pipeOut)
		val currentSink = new SyncVar[Option[OutputStream]]
		val sink = new PipeSink(pipeIn, currentSink, b.toString)
		sink.start()

		def handleOutOrError(fromOutput: InputStream) = currentSource.put(Some(fromOutput))

		val firstIO =
			if(toError)
				defaultIO.withError(handleOutOrError)
			else
				defaultIO.withOutput(handleOutOrError)
		val secondIO = defaultIO.withInput(toInput => currentSink.put(Some(toInput)) )
		
		val second = b.run(secondIO)
		val first = a.run(firstIO)
		try
		{
			runInterruptible {
				first.exitValue
				currentSource.put(None)
				currentSink.put(None)
				val result = second.exitValue
				result
			} {
				first.destroy()
				second.destroy()
			}
		}
		finally
		{
			BasicIO.close(pipeIn)
			BasicIO.close(pipeOut)
		}
	}
}
private class PipeSource(currentSource: SyncVar[Option[InputStream]], pipe: PipedOutputStream, label: => String) extends Thread
{
	final override def run()
	{
		currentSource.get match
		{
			case Some(source) =>
				try { BasicIO.transferFully(source, pipe) }
				catch { case e: IOException => println("I/O error " + e.getMessage + " for process: " + label); e.printStackTrace() }
				finally
				{
					BasicIO.close(source)
					currentSource.unset()
				}
				run()
			case None =>
				currentSource.unset()
				BasicIO.close(pipe)
		}
	}
}
private class PipeSink(pipe: PipedInputStream, currentSink: SyncVar[Option[OutputStream]], label: => String) extends Thread
{
	final override def run()
	{
		currentSink.get match
		{
			case Some(sink) =>
				try { BasicIO.transferFully(pipe, sink) }
				catch { case e: IOException => println("I/O error " + e.getMessage + " for process: " + label); e.printStackTrace() }
				finally
				{
					BasicIO.close(sink)
					currentSink.unset()
				}
				run()
			case None =>
				currentSink.unset()
		}
	}
}

private[sbt] class DummyProcessBuilder(override val toString: String, exitValue : => Int) extends AbstractProcessBuilder
{
	override def run(io: ProcessIO): Process = new DummyProcess(exitValue)
	override def canPipeTo = true
}
/** A thin wrapper around a java.lang.Process.  `ioThreads` are the Threads created to do I/O.
* The implementation of `exitValue` waits until these threads die before returning. */
private class DummyProcess(action: => Int) extends Process
{
	private[this] val exitCode = Future(action)
	override def exitValue() = exitCode()
	override def destroy() {}
}
/** Represents a simple command without any redirection or combination. */
private[sbt] class SimpleProcessBuilder(p: JProcessBuilder) extends AbstractProcessBuilder
{
	override def run(io: ProcessIO): Process =
	{
		val process = p.start() // start the external process
		import io.{writeInput, processOutput, processError}
		// spawn threads that process the input, output, and error streams using the functions defined in `io`
		val inThread = Spawn(writeInput(process.getOutputStream), true)
		val outThread = Spawn(processOutput(process.getInputStream))
		val errorThread =
			if(!p.redirectErrorStream)
				Spawn(processError(process.getErrorStream)) :: Nil
			else
				Nil
		new SimpleProcess(process, inThread, outThread :: errorThread)
	}
	override def toString = p.command.toString
	override def canPipeTo = true
}
/** A thin wrapper around a java.lang.Process.  `outputThreads` are the Threads created to read from the
* output and error streams of the process.  `inputThread` is the Thread created to write to the input stream of
* the process.
* The implementation of `exitValue` interrupts `inputThread` and then waits until all I/O threads die before
* returning. */
private class SimpleProcess(p: JProcess, inputThread: Thread, outputThreads: List[Thread]) extends Process
{
	override def exitValue() =
	{
		try { p.waitFor() }// wait for the process to terminate
		finally { inputThread.interrupt() } // we interrupt the input thread to notify it that it can terminate
		outputThreads.foreach(_.join()) // this ensures that all output is complete before returning (waitFor does not ensure this)
		p.exitValue()
	}
	override def destroy() =
	{
		try { p.destroy() }
		finally { inputThread.interrupt() }
	}
}

private class FileOutput(file: File, append: Boolean) extends OutputStreamBuilder(new FileOutputStream(file, append), file.getAbsolutePath)
private class URLInput(url: URL) extends InputStreamBuilder(url.openStream, url.toString)
private class FileInput(file: File) extends InputStreamBuilder(new FileInputStream(file), file.getAbsolutePath)

import Uncloseable.protect
private class OutputStreamBuilder(stream: => OutputStream, label: String) extends ThreadProcessBuilder(label, _.writeInput(protect(stream)))
{
	def this(stream: => OutputStream) = this(stream, "<output stream>")
}
private class InputStreamBuilder(stream: => InputStream, label: String) extends ThreadProcessBuilder(label, _.processOutput(protect(stream)))
{
	def this(stream: => InputStream) = this(stream, "<input stream>")
}

private abstract class ThreadProcessBuilder(override val toString: String, runImpl: ProcessIO => Unit) extends AbstractProcessBuilder
{
	override def run(io: ProcessIO): Process =
	{
		val success = new SyncVar[Boolean]
		success.put(false)
		new ThreadProcess(Spawn {runImpl(io); success.set(true) }, success)
	}
}
private final class ThreadProcess(thread: Thread, success: SyncVar[Boolean]) extends Process
{
	override def exitValue() =
	{
		thread.join()
		if(success.get) 0 else 1
	}
	override def destroy() { thread.interrupt() }
}

object Uncloseable
{
	def apply(in: InputStream): InputStream = new FilterInputStream(in) { override def close() {} }
	def apply(out: OutputStream): OutputStream = new FilterOutputStream(out) { override def close() {} }
	def protect(in: InputStream): InputStream = if(in eq System.in) Uncloseable(in) else in
	def protect(out: OutputStream): OutputStream = if( (out eq System.out) || (out eq System.err)) Uncloseable(out) else out
}
private object Streamed
{
	def apply[T](nonzeroException: Boolean): Streamed[T] =
	{
		val q = new java.util.concurrent.LinkedBlockingQueue[Either[Int, T]]
		def next(): Stream[T] =
			q.take match
			{
				case Left(0) => Stream.empty
				case Left(code) => if(nonzeroException) error("Nonzero exit code: " + code) else Stream.empty
				case Right(s) => Stream.cons(s, next)
			}
		new Streamed((s: T) => q.put(Right(s)), code => q.put(Left(code)), () => next())
	}
}

private final class Streamed[T](val process: T => Unit, val done: Int => Unit, val stream: () => Stream[T]) extends NotNull