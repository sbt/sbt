/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.LinkedBlockingQueue
import verify.BasicTestSuite

object UTF8DecoderSpec extends BasicTestSuite:
  val decoder: java.nio.charset.CharsetDecoder = Charset.forName("UTF-8").newDecoder

  test("ascii characters should not be modified"):
    val inputStream = new InputStream:
      override def read(): Int = 'c'.toInt
    assert(JLine3.decodeInput(decoder, inputStream) == 'c'.toInt)

  test("swedish characters should be handled"):
    val bytes = new LinkedBlockingQueue[Int]
    // these are the utf-8 codes for an umlauted a in swedish
    Seq(195, 164).foreach(b => bytes.put(b))
    val inputStream = new InputStream:
      override def read(): Int = Option(bytes.poll).getOrElse(-1)
    assert(JLine3.decodeInput(decoder, inputStream) == 228)

  test("emoji should be handled"):
    val bytes = new LinkedBlockingQueue[Int]
    // laughing and crying emoji in utf8
    Seq(0xf0, 0x9f, 0x98, 0x82).foreach(b => bytes.put(b))
    val inputStream = new InputStream:
      override def read(): Int = Option(bytes.poll).getOrElse(-1)
    assert(JLine3.decodeInput(decoder, inputStream) == 128514)
end UTF8DecoderSpec
