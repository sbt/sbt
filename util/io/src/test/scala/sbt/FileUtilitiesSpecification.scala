/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

import org.scalacheck._
import Prop._
import Arbitrary.{ arbString => _, arbChar => _, _ }
import java.io.{ File, IOException }

object WriteContentSpecification extends Properties("Write content") {
  property("Roundtrip string") = forAll(writeAndCheckString _)
  property("Roundtrip bytes") = forAll(writeAndCheckBytes _)
  property("Write string overwrites") = forAll(overwriteAndCheckStrings _)
  property("Write bytes overwrites") = forAll(overwriteAndCheckBytes _)
  property("Append string appends") = forAll(appendAndCheckStrings _)
  property("Append bytes appends") = forAll(appendAndCheckBytes _)
  property("Unzip doesn't stack overflow") = largeUnzip()

  implicit lazy val validChar: Arbitrary[Char] = Arbitrary(for (i <- Gen.choose(0, 0xd7ff)) yield i.toChar)
  implicit lazy val validString: Arbitrary[String] = Arbitrary(arbitrary[List[Char]] map (_.mkString))

  private def largeUnzip() =
    {
      testUnzip[Product]
      testUnzip[scala.tools.nsc.Global]
      true
    }
  private def testUnzip[T](implicit mf: scala.reflect.Manifest[T]) =
    unzipFile(IO.classLocationFile(mf.runtimeClass))
  private def unzipFile(jar: File) =
    IO.withTemporaryDirectory { tmp =>
      IO.unzip(jar, tmp)
    }

  // make the test independent of underlying platform and allow any unicode character in Strings to be encoded
  val charset = IO.utf8

  import IO._
  private def writeAndCheckString(s: String) =
    withTemporaryFile { file =>
      write(file, s, charset)
      read(file, charset) == s
    }
  private def writeAndCheckBytes(b: Array[Byte]) =
    withTemporaryFile { file =>
      write(file, b)
      readBytes(file) sameElements b
    }
  private def overwriteAndCheckStrings(a: String, b: String) =
    withTemporaryFile { file =>
      write(file, a, charset)
      write(file, b, charset)
      read(file, charset) == b
    }
  private def overwriteAndCheckBytes(a: Array[Byte], b: Array[Byte]) =
    withTemporaryFile { file =>
      write(file, a)
      write(file, b)
      readBytes(file) sameElements b
    }
  private def appendAndCheckStrings(a: String, b: String) =
    withTemporaryFile { file =>
      append(file, a, charset)
      append(file, b, charset)
      read(file, charset) == (a + b)
    }
  private def appendAndCheckBytes(a: Array[Byte], b: Array[Byte]) =
    withTemporaryFile { file =>
      append(file, a)
      append(file, b)
      readBytes(file) sameElements (a ++ b)
    }

  private def withTemporaryFile[T](f: File => T): T =
    withTemporaryDirectory { dir => f(new java.io.File(dir, "out")) }
}
