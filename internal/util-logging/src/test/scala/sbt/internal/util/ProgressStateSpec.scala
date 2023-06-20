/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.{ File, PrintStream }

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.BeforeAndAfterAll
import sbt.internal.util.Terminal.SimpleTerminal

import scala.io.Source

class ProgressStateSpec extends AnyFlatSpec with BeforeAndAfterAll {

  private lazy val fileIn = new File("/tmp/tmp.txt")
  private lazy val fileOut = Source.fromFile("/tmp/tmp.txt")

  override def afterAll(): Unit = {
    fileIn.delete()
    fileOut.close()
    super.afterAll()
  }

  "test" should "not clear after carriage return (\\r) " in {
    val ps = new ProgressState(1, 8)
    val in = "Hello\r\nWorld".getBytes()

    ps.write(SimpleTerminal, in, new PrintStream(fileIn), hasProgress = true)

    val clearScreenBytes = ConsoleAppender.ClearScreenAfterCursor.getBytes("UTF-8")
    val check = fileOut.getLines().toList.map { line =>
      line.getBytes("UTF-8").endsWith(clearScreenBytes)
    }

    assert(check === List(false, true))
  }
}
