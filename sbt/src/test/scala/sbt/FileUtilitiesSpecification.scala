/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

import org.scalacheck._
import java.io.File

object WriteContentSpecification extends Properties("Write content")
{
	val log = new ConsoleLogger
	log.setLevel(Level.Warn)
	
	specify("Roundtrip string", writeAndCheckString _)
	specify("Roundtrip bytes", writeAndCheckBytes _)
	specify("Write string overwrites", overwriteAndCheckStrings _)
	specify("Write bytes overwrites", overwriteAndCheckBytes _)
	specify("Append string appends", appendAndCheckStrings _)
	specify("Append bytes appends", appendAndCheckBytes _)

	// make the test independent of underlying platform and allow any unicode character in Strings to be encoded
	val charset = java.nio.charset.Charset.forName("UTF-8")
		
	import FileUtilities._
	private def writeAndCheckString(s: String) =
	{
		val result = withTemporaryFile( file => writeThen(file, s)( readString(file, charset, log) ) )
		handleResult[String](result, _ == s)
	}
	private def writeAndCheckBytes(b: Array[Byte]) =
	{
		val result = withTemporaryFile( file => writeThen(file, b)( readBytes(file, log) ) )
		handleResult[Array[Byte]](result, _ deepEquals b)
	}
	private def overwriteAndCheckStrings(a: String, b: String) =
	{
		val result = withTemporaryFile( file => writeThen(file, a)( writeThen(file, b)( readString(file, charset, log) ) ) )
		handleResult[String](result, _ == b)
	}
	private def overwriteAndCheckBytes(a: Array[Byte], b: Array[Byte]) =
	{
		val result = withTemporaryFile( file => writeThen(file, a)( writeThen(file, b)( readBytes(file, log) ) ) )
		handleResult[Array[Byte]](result, _ deepEquals b)
	}
	private def appendAndCheckStrings(a: String, b: String) =
	{
		val result = withTemporaryFile( file => appendThen(file, a)( appendThen(file, b)( readString(file, charset, log) ) ) )
		handleResult[String](result, _ == (a+b))
	}
	private def appendAndCheckBytes(a: Array[Byte], b: Array[Byte]) =
	{
		val result = withTemporaryFile( file => appendThen(file, a)( appendThen(file, b)( readBytes(file, log) ) ) )
		handleResult[Array[Byte]](result, _ deepEquals (a++b))
	}
	
	private def withTemporaryFile[T](f: File => Either[String, T]): Either[String, T] =
			doInTemporaryDirectory(log) { dir => f(new java.io.File(dir, "out")) }
				
	private def handleResult[T](result: Either[String, T], check: T => Boolean): Boolean =
		result match
		{
			case Left(err) => log.trace(new RuntimeException(err)); log.error(err); false
			case Right(x) => check(x)
		}
	private def writeThen[T](file: File, content: String)(action: => Either[String, T]) =
		write(file, content, charset, log).toLeft(()).right.flatMap { x =>action }
	private def writeThen[T](file: File, content: Array[Byte])(action: => Either[String, T]) =
		write(file, content, log).toLeft(()).right.flatMap { x =>action }
	private def appendThen[T](file: File, content: String)(action: => Either[String, T]) =
		append(file, content, charset, log).toLeft(()).right.flatMap { x =>action }
	private def appendThen[T](file: File, content: Array[Byte])(action: => Either[String, T]) =
		append(file, content, log).toLeft(()).right.flatMap { x =>action }
}