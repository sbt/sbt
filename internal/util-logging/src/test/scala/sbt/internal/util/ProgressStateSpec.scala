/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.{ File, PrintStream }

import verify.BasicTestSuite
import sbt.internal.util.Terminal.SimpleTerminal

import scala.io.Source
import scala.util.Using

object ProgressStateSpec extends BasicTestSuite:

  test("test should not clear after carriage return (\\r)"):
    val fileIn = new File("/tmp/tmp.txt")
    try
      val ps = new ProgressState(1, 8)
      val in = "Hello\r\nWorld".getBytes()

      ps.write(SimpleTerminal, in, new PrintStream(fileIn), hasProgress = true)

      Using.resource(Source.fromFile("/tmp/tmp.txt")) { fileOut =>
        val clearScreenBytes = ConsoleAppender.ClearScreenAfterCursor.getBytes("UTF-8")
        val check = fileOut.getLines().toList.map { line =>
          line.getBytes("UTF-8").endsWith(clearScreenBytes)
        }
        assert(check == List(false, true))
      }
    finally
      fileIn.delete()
      ()
end ProgressStateSpec
