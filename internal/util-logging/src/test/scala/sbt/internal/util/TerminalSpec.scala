/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{ Signaler, ThreadSignaler, TimeLimitedTests }
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.time.Span
import org.scalatest.time.SpanSugar._
import sbt.internal.util.Terminal.{ SimpleTerminal, WriteableInputStream }

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream }

class TerminalSpec extends AnyFlatSpec with TimeLimitedTests with BeforeAndAfterEach {

  override val timeLimit: Span = 200 milliseconds

  override val defaultTestSignaler: Signaler = ThreadSignaler

  override protected def afterEach(): Unit = {
    // Set this back to the initial terminal otherwise it breaks downstream tests.
    val _ = Terminal.set(SimpleTerminal)
  }

  // This sets the system property which causes the proxyInputStream to forever think
  // that it is in scripted mode. Only run this when necessary.
  //"Terminal withStreams for a scripted test" should "have no bytes available" in {
  //  val byteCount = 0
  //  System.setProperty("sbt.scripted", "true")
  //  Terminal.withStreams(isServer = false, isSubProcess = true) {
  //    Terminal.console.write('x' * byteCount)
  //    System.in.available() shouldBe byteCount
  //    System.in.skip(byteCount.toLong) shouldBe byteCount.toLong
  //  }
  //}

  "Terminal withStreams for a sub process" should "have written bytes available" in {
    val byteCount = 8
    val bytes = Array.fill(byteCount)(0)
    // isSubProcess = true so we get a console terminal which we can write to and have it come back on stdin.
    Terminal.withStreams(isServer = false, isSubProcess = true) {
      Terminal.console.write(bytes: _*)
      System.in.available() shouldBe byteCount
      System.in.skip(byteCount.toLong) shouldBe byteCount.toLong
    }
  }

  "Terminal withStreams for a sub process" should "have written bytes available from boot stream" in {
    val byteCount = 8
    val bytes = Array.fill(byteCount)(0)
    val existingByteCount = 5
    val existingBytes = Array.fill(existingByteCount)(0.toByte)
    val totalByteCount = byteCount + existingByteCount
    withCloseable(writableInputStream(existingBytes)) { is =>
      withCloseable(outputStream(byteCount)) { os =>
        withBootStreams(is, os) {
          // isServer = true otherwise it closes the console and messes up other tests.
          // isSubProcess = true so we get a console terminal which we can write to and have it come back on stdin.
          Terminal.withStreams(isServer = false, isSubProcess = true) {
            Terminal.console.write(bytes: _*)
            System.in.available() shouldBe totalByteCount
            System.in.skip(totalByteCount.toLong) shouldBe totalByteCount.toLong
          }
        }
      }
    }
  }

  private def writableInputStream(existingBytes: Array[Byte]) =
    new WriteableInputStream(new ByteArrayInputStream(existingBytes), getClass.getSimpleName)

  private def outputStream(size: Int) =
    new ByteArrayOutputStream(size)

  private def withCloseable[A, C <: AutoCloseable](closeable: => C)(f: C => A): A = {
    val c = closeable
    try {
      f(c)
    } finally {
      c.close()
    }
  }

  private def withBootStreams[A](bootInputStream: InputStream, bootOutputStream: OutputStream)(
      f: => A
  ): A = {
    try {
      Terminal.setBootStreams(bootInputStream, bootOutputStream)
      f
    } finally {
      // Don't have much choice but to set these back to null which is what they are on initialization.
      Terminal.setBootStreams(null, null)
    }
  }
}
