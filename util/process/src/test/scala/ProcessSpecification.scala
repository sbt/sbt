package sbt

import java.io.File
import org.scalacheck.{ Arbitrary, Gen, Prop, Properties }
import Prop._

import Process._

object ProcessSpecification extends Properties("Process I/O") {
  implicit val exitCodeArb: Arbitrary[Array[Byte]] = Arbitrary(
    for (size <- Gen.choose(0, 10);
         l <- Gen.listOfN[Byte](size, Arbitrary.arbByte.arbitrary)) yield l.toArray
  )

  /*property("Correct exit code") = forAll( (exitCode: Byte) => checkExit(exitCode))
	property("#&& correct") = forAll( (exitCodes: Array[Byte]) => checkBinary(exitCodes)(_ #&& _)(_ && _))
	property("#|| correct") = forAll( (exitCodes: Array[Byte]) => checkBinary(exitCodes)(_ #|| _)(_ || _))
	property("### correct") = forAll( (exitCodes: Array[Byte]) => checkBinary(exitCodes)(_ ### _)( (x,latest) => latest))*/
  property("Pipe to output file") = forAll((data: Array[Byte]) => checkFileOut(data))
  property("Pipe from input file") = forAll((data: Array[Byte]) => checkFileIn(data))
  property("Pipe to process") = forAll((data: Array[Byte]) => checkPipe(data))
  property("Pipe to process ignores input exit code") = forAll(
    (data: Array[Byte], code: Byte) => checkPipeExit(data, code)
  )
  property("Pipe from input file to bad process preserves correct exit code.") = forAll(
    (data: Array[Byte], code: Byte) => checkFileInExit(data, code)
  )
  property("Pipe to output file from bad process preserves correct exit code.") = forAll(
    (data: Array[Byte], code: Byte) => checkFileOutExit(data, code)
  )

  private def checkBinary(codes: Array[Byte])(
      reduceProcesses: (ProcessBuilder, ProcessBuilder) => ProcessBuilder
  )(reduceExit: (Boolean, Boolean) => Boolean) =
    (codes.length > 1) ==> {
      val unsignedCodes = codes.map(unsigned)
      val exitCode =
        unsignedCodes.map(code => Process(process("sbt.exit " + code))).reduceLeft(reduceProcesses) !
      val expectedExitCode = unsignedCodes.map(toBoolean).reduceLeft(reduceExit)
      toBoolean(exitCode) == expectedExitCode
    }
  private def toBoolean(exitCode: Int) = exitCode == 0
  private def checkExit(code: Byte) = {
    val exitCode = unsigned(code)
    (process("sbt.exit " + exitCode) !) == exitCode
  }
  private def checkFileOut(data: Array[Byte]) =
    withData(data) { (temporaryFile, temporaryFile2) =>
      val catCommand = process("sbt.cat " + temporaryFile.getAbsolutePath)
      catCommand #> temporaryFile2
    }
  private def checkFileIn(data: Array[Byte]) =
    withData(data) { (temporaryFile, temporaryFile2) =>
      val catCommand = process("sbt.cat")
      temporaryFile #> catCommand #> temporaryFile2
    }
  private def checkPipe(data: Array[Byte]) =
    withData(data) { (temporaryFile, temporaryFile2) =>
      val catCommand = process("sbt.cat")
      temporaryFile #> catCommand #| catCommand #> temporaryFile2
    }
  private def checkPipeExit(data: Array[Byte], code: Byte) =
    withTempFiles { (a, b) =>
      IO.write(a, data)
      val catCommand = process("sbt.cat")
      val exitCommand = process(s"sbt.exit $code")
      val exit = (a #> exitCommand #| catCommand #> b).!
      (s"Exit code: $exit") |:
        (s"Output file length: ${b.length}") |:
        (exit == 0) &&
      (b.length == 0)
    }

  private def checkFileOutExit(data: Array[Byte], exitCode: Byte) =
    withTempFiles { (a, b) =>
      IO.write(a, data)
      val code = unsigned(exitCode)
      val command = process(s"sbt.exit $code")
      val exit = (a #> command #> b).!
      (s"Exit code: $exit, expected: $code") |:
        (s"Output file length: ${b.length}") |:
        (exit == code) &&
      (b.length == 0)
    }

  private def checkFileInExit(data: Array[Byte], exitCode: Byte) =
    withTempFiles { (a, b) =>
      IO.write(a, data)
      val code = unsigned(exitCode)
      val command = process(s"sbt.exit $code")
      val exit = (a #> command).!
      (s"Exit code: $exit, expected: $code") |:
        (exit == code)
    }

  private def temp() = File.createTempFile("sbt", "")
  private def withData(data: Array[Byte])(f: (File, File) => ProcessBuilder) =
    withTempFiles { (a, b) =>
      IO.write(a, data)
      val process = f(a, b)
      (process !) == 0 && sameFiles(a, b)
    }
  private def sameFiles(a: File, b: File) =
    IO.readBytes(a) sameElements IO.readBytes(b)

  private def withTempFiles[T](f: (File, File) => T): T = {
    val temporaryFile1 = temp()
    val temporaryFile2 = temp()
    try f(temporaryFile1, temporaryFile2)
    finally {
      temporaryFile1.delete()
      temporaryFile2.delete()
    }
  }
  private def unsigned(b: Int): Int = ((b: Int) + 256) % 256
  private def unsigned(b: Byte): Int = unsigned(b: Int)
  private def process(command: String) = {
    val ignore = echo // just for the compile dependency so that this test is rerun when TestedProcess.scala changes, not used otherwise

    val thisClasspath =
      List(getSource[Product], getSource[IO.type], getSource[SourceTag]).mkString(File.pathSeparator)
    "java -cp " + thisClasspath + " " + command
  }
  private def getSource[T: Manifest]: String =
    IO.classLocationFile[T].getAbsolutePath
}
private trait SourceTag
