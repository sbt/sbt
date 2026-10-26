/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.scripted

import java.io.{ ByteArrayInputStream, File, PipedInputStream, PipedOutputStream }
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.{ CompletableFuture, CountDownLatch, TimeUnit }
import scala.util.Using
import hedgehog.*
import hedgehog.runner.*

object FileCommandsTest extends Properties:
  override def tests: List[Test] = List(
    example("pause waits for a line on standard input", waitsForLine),
    example("pause accepts end of standard input", endOfInput),
  )

  def waitsForLine: Result =
    val reading = new CountDownLatch(1)
    Using.resources(
      new PipedInputStream():
        override def read(): Int =
          reading.countDown()
          super.read()
      ,
      new PipedOutputStream()
    ): (input, output) =>
      output.connect(input)
      val finished = new CompletableFuture[String]()
      val worker = new Thread(() =>
        try
          Console.withIn(input):
            new FileCommands(new File(".")).apply("pause", Nil)
            finished.complete(scala.io.StdIn.readLine())
            ()
        catch
          case e: Throwable =>
            finished.completeExceptionally(e)
            ()
      )
      worker.setDaemon(true)
      worker.start()
      val startedReading = reading.await(5, TimeUnit.SECONDS)
      val blocked = !finished.isDone
      output.write("\nnext line\n".getBytes(UTF_8))
      output.flush()
      val nextLine = finished.get(5, TimeUnit.SECONDS)
      Result.assert(startedReading && blocked && nextLine == "next line")
  end waitsForLine

  def endOfInput: Result =
    Using.resource(new ByteArrayInputStream(Array.emptyByteArray)): input =>
      Console.withIn(input):
        new FileCommands(new File(".")).apply("pause", Nil)
      Result.success
end FileCommandsTest
