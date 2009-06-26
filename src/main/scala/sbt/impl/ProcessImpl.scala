/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package sbt

import java.lang.{Process => JProcess, ProcessBuilder => JProcessBuilder}
import java.io.{BufferedReader, Closeable, InputStream, InputStreamReader, IOException, OutputStream}
import java.io.{PipedInputStream, PipedOutputStream}
import java.io.{File, FileInputStream, FileOutputStream}
import java.net.URL

import scala.concurrent.ops.future
import scala.concurrent.SyncVar

/** Runs provided code in a new Thread and returns the Thread instance. */
private object Spawn
{
	def apply(f: => Unit): Thread =
	{
		val thread = new Thread() { override def run() = { f } }
		thread.start()
		thread
	}
}

private object BasicIO
{
	def apply(log: Logger) = new ProcessIO(ignoreOut, processFully(log, Level.Info), processFully(log, Level.Error))

	def ignoreOut = (i: OutputStream) => ()
	val BufferSize = 8192
	def close(c: java.io.Closeable) = try { c.close() } catch { case _: java.io.IOException => () }
	def processFully(log: Logger, level: Level.Value)(i: InputStream) { processFully(line => log.log(level, line))(i) }
	def processFully(processLine: String => Unit)(i: InputStream)
	{
		val reader = new BufferedReader(new InputStreamReader(i))
		def readFully()
		{
			val line = reader.readLine()
			if(line != null)
			{
				processLine(line)
				readFully()
			}
		}
		readFully()
	}
	def standard: ProcessIO = new ProcessIO(ignoreOut, processFully(System.out.println), processFully(System.err.println))

	def transferFully(in: InputStream, out: OutputStream)
	{
		val continueCount = 1//if(in.isInstanceOf[PipedInputStream]) 1 else 0
		val buffer = new Array[Byte](BufferSize)
		def read
		{
			val byteCount = in.read(buffer)
			if(byteCount >= continueCount)
			{
				out.write(buffer, 0, byteCount)
				read
			}
		}
		read
	}
}


private abstract class AbstractProcessBuilder extends ProcessBuilder
{
	def #&&(other: ProcessBuilder): ProcessBuilder = new AndProcessBuilder(this, other)
	def #||(other: ProcessBuilder): ProcessBuilder = new OrProcessBuilder(this, other)
	def #|(other: ProcessBuilder): ProcessBuilder =
	{
		require(other.canPipeTo, "Piping to multiple processes is not supported.")
		new PipedProcessBuilder(this, other, false)
	}
	def ##(other: ProcessBuilder): ProcessBuilder = new SequenceProcessBuilder(this, other)
	
	def #< (f: File): ProcessBuilder = new PipedProcessBuilder(new FileInput(f), this, false)
	def #< (url: URL): ProcessBuilder = new PipedProcessBuilder(new URLInput(url), this, false)
	def #> (f: File): ProcessBuilder = new PipedProcessBuilder(this, new FileOutput(f, false), false)
	def #>> (f: File): ProcessBuilder = new PipedProcessBuilder(this, new FileOutput(f, true), true)
	
	def run(): Process = run(BasicIO.standard)
	def run(log: Logger): Process = run(BasicIO(log))
	
	def ! = run().exitValue()
	def !(log: Logger) =
	{
		val log2 = new BufferedLogger(log)
		log2.startRecording()
		try { run(log2).exitValue() }
		finally { log2.playAll(); log2.clearAll() }
	}
	def !(io: ProcessIO) = run(io).exitValue()

	def canPipeTo = false
}
private[sbt] class URLBuilder(url: URL) extends URLPartialBuilder
{
	def #>(b: ProcessBuilder): ProcessBuilder = b #< url
	def #>>(file: File): ProcessBuilder = toFile(file, true)
	def #>(file: File): ProcessBuilder = toFile(file, false)
	private def toFile(file: File, append: Boolean) = new PipedProcessBuilder(new URLInput(url), new FileOutput(file, append), false)
}
private[sbt] class FileBuilder(base: File) extends FilePartialBuilder
{
	def #>(b: ProcessBuilder): ProcessBuilder = b #< base
	def #<(b: ProcessBuilder): ProcessBuilder = b #> base
	def #<(url: URL): ProcessBuilder = new URLBuilder(url) #> base
	def #>>(file: File): ProcessBuilder = pipe(base, file, true)
	def #>(file: File): ProcessBuilder = pipe(base, file, false)
	def #<(file: File): ProcessBuilder = pipe(file, base, false)
	def #<<(file: File): ProcessBuilder = pipe(file, base, true)
	private def pipe(from: File, to: File, append: Boolean) = new PipedProcessBuilder(new FileInput(from), new FileOutput(to, append), false)
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
			future { thread.join(); code.get },
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
private class SequenceProcessBuilder(first: ProcessBuilder, second: ProcessBuilder) extends SequentialProcessBuilder(first, second, "##")
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


/** Represents a simple command without any redirection or combination. */
private[sbt] class SimpleProcessBuilder(p: JProcessBuilder) extends AbstractProcessBuilder
{
	override def run(io: ProcessIO): Process =
	{
		val process = p.start() // start the external process
		import io.{writeInput, processOutput, processError}
		// spawn threads that process the input, output, and error streams using the functions defined in `io`
		val inThread = Spawn(writeInput(process.getOutputStream))
		val outThread = Spawn(processOutput(process.getInputStream))
		val errorThread =
			if(!p.redirectErrorStream)
				Spawn(processError(process.getErrorStream)) :: Nil
			else
				Nil
		new SimpleProcess(process, inThread :: outThread :: errorThread)
	}
	override def toString = p.command.toString
	override def canPipeTo = true
}
/** A thin wrapper around a java.lang.Process.  `ioThreads` are the Threads created to do I/O.
* The implementation of `exitValue` waits until these threads die before returning. */
private class SimpleProcess(p: JProcess, ioThreads: Iterable[Thread]) extends Process
{
	override def exitValue() =
	{
		p.waitFor() // wait for the process to terminate
		ioThreads.foreach(_.join()) // this ensures that all output is complete before returning (waitFor does not ensure this)
		p.exitValue()
	}
	override def destroy() = p.destroy()
}

private class FileOutput(file: File, append: Boolean) extends OutputStreamBuilder(new FileOutputStream(file, append), file.getAbsolutePath)
private class URLInput(url: URL) extends InputStreamBuilder(url.openStream, url.toString)
private class FileInput(file: File) extends InputStreamBuilder(new FileInputStream(file), file.getAbsolutePath)

private class OutputStreamBuilder(stream: => OutputStream, label: String) extends ThreadProcessBuilder(label, _.writeInput(stream))
private class InputStreamBuilder(stream: => InputStream, label: String) extends ThreadProcessBuilder(label, _.processOutput(stream))

private abstract class ThreadProcessBuilder(override val toString: String, runImpl: ProcessIO => Unit) extends AbstractProcessBuilder
{
	override def run(io: ProcessIO): Process =
	{
		val success = new SyncVar[Boolean]
		success.put(false)
		new ThreadProcess(Spawn {runImpl(io); success.set(true) }, success)
	}
}
private class ThreadProcess(thread: Thread, success: SyncVar[Boolean]) extends Process
{
	override def exitValue() =
	{
		thread.join()
		if(success.get) 0 else 1
	}
	override def destroy() { thread.interrupt() }
}