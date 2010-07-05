package sbt

import java.io.File
import org.scalacheck.{Arbitrary, Gen, Prop, Properties}
import Prop._

import Process._

object ProcessSpecification extends Properties("Process I/O")
{
	private val log = new ConsoleLogger

	implicit val exitCodeArb: Arbitrary[Array[Byte]] = Arbitrary(Gen.choose(0, 10) flatMap { size => Gen.resize(size, Arbitrary.arbArray[Byte].arbitrary) })

	/*property("Correct exit code") = forAll( (exitCode: Byte) => checkExit(exitCode))
	property("#&& correct") = forAll( (exitCodes: Array[Byte]) => checkBinary(exitCodes)(_ #&& _)(_ && _))
	property("#|| correct") = forAll( (exitCodes: Array[Byte]) => checkBinary(exitCodes)(_ #|| _)(_ || _))
	property("### correct") = forAll( (exitCodes: Array[Byte]) => checkBinary(exitCodes)(_ ### _)( (x,latest) => latest))*/
	property("Pipe to output file") = forAll( (data: Array[Byte]) => checkFileOut(data))
	property("Pipe to input file") = forAll( (data: Array[Byte]) => checkFileIn(data))
	property("Pipe to process") = forAll( (data: Array[Byte]) => checkPipe(data))

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
			IO.write(temporaryFile1, data)
			val process = f(temporaryFile1, temporaryFile2)
			( process ! ) == 0 &&
			{
				val b1 = IO.readBytes(temporaryFile1)
				val b2 = IO.readBytes(temporaryFile2)
				b1 sameElements b2
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

		val thisClasspath = List(getSource[ScalaObject], getSource[IO.type], getSource[SourceTag]).mkString(File.pathSeparator)
		"java -cp " + thisClasspath + " " + command
	}
	private def getSource[T : Manifest]: String =
		IO.classLocationFile[T].getAbsolutePath
}
private trait SourceTag