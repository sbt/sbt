package sbt

import java.io.File
import org.scalacheck.{Prop, Properties}
import Prop._

import Process._

object ProcessSpecification extends Properties("Process I/O")
{
	private val log = new ConsoleLogger
	
	specify("Correct exit code", (exitCode: Byte) => checkExit(exitCode))
	specify("#&& correct", (exitCodes: Array[Byte]) => checkBinary(exitCodes)(_ #&& _)(_ && _))
	specify("#|| correct", (exitCodes: Array[Byte]) => checkBinary(exitCodes)(_ #|| _)(_ || _))
	specify("## correct", (exitCodes: Array[Byte]) => checkBinary(exitCodes)(_ ## _)( (x,latest) => latest))
	specify("Pipe to output file", (data: Array[Byte]) => checkFileOut(data))
	specify("Pipe to input file", (data: Array[Byte]) => checkFileIn(data))
	specify("Pipe to process", (data: Array[Byte]) => checkPipe(data))

	private def checkBinary(codes: Array[Byte])(reduceProcesses: (ProcessBuilder, ProcessBuilder) => ProcessBuilder)(reduceExit: (Boolean, Boolean) => Boolean) =
	{
		(codes.length > 1) ==>
		{
			val unsignedCodes = codes.map(unsigned)
			val exitCode = unsignedCodes.map(code => Process(process("sbt.exit " + code))).reduceLeft(reduceProcesses) !
			val expectedExitCode = unsignedCodes.map(toBoolean).reduceLeft(reduceExit)
			toBoolean(exitCode) == expectedExitCode
		}
	}
	private def toBoolean(exitCode: Int) = exitCode == 0
	private def checkExit(code: Byte) = 
	{
		val exitCode = unsigned(code)
		(process("sbt.exit " + exitCode) !) == exitCode
	}
	private def checkFileOut(data: Array[Byte]) =
	{
		withData(data) { (temporaryFile, temporaryFile2) =>
			val catCommand = process("sbt.cat " + temporaryFile.getAbsolutePath)
			catCommand #> temporaryFile2
		}
	}
	private def checkFileIn(data: Array[Byte]) =
	{
		withData(data) { (temporaryFile, temporaryFile2) =>
			val catCommand = process("sbt.cat")
			temporaryFile #> catCommand #> temporaryFile2
		}
	}
	private def checkPipe(data: Array[Byte]) =
	{
		withData(data) { (temporaryFile, temporaryFile2) =>
			val catCommand = process("sbt.cat")
			temporaryFile #> catCommand #| catCommand #> temporaryFile2
		}
	}
	private def temp() = File.createTempFile("sbt", "")
	private def withData(data: Array[Byte])(f: (File, File) => ProcessBuilder) =
	{
		val temporaryFile1 = temp()
		val temporaryFile2 = temp()
		try
		{
			FileUtilities.write(temporaryFile1, data, log)
			val process = f(temporaryFile1, temporaryFile2)
			( process ! ) == 0 &&
			{
				val result =
					for(b1 <- FileUtilities.readBytes(temporaryFile1, log).right; b2 <- FileUtilities.readBytes(temporaryFile2, log).right) yield
						b1 deepEquals b2
				result.fold(error, x => x)
			}
		}
		finally
		{
			temporaryFile1.delete()
			temporaryFile2.delete()
		}
	}
	private def unsigned(b: Byte): Int = ((b: Int) +256) % 256
	private def process(command: String) =
	{
		val ignore = echo // just for the compile dependency so that this test is rerun when TestedProcess.scala changes, not used otherwise
		
		val thisClasspath = List(getSource[ScalaObject], getSource[sbt.Logger], getSource[sbt.SourceTag]).mkString(File.pathSeparator)
		"java -cp " + thisClasspath + " " + command
	}
	private def getSource[T](implicit mf: scala.reflect.Manifest[T]): String =
		(FileUtilities.toFile(mf.erasure.getProtectionDomain.getCodeSource.getLocation)).getAbsolutePath
}
private trait SourceTag