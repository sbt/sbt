/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.InputStream
import java.nio.charset.Charset
import org.scalatest.FlatSpec
import java.util.concurrent.LinkedBlockingQueue

class UTF8DecoderSpec extends FlatSpec {
  val decoder = Charset.forName("UTF-8").newDecoder
  "ascii characters" should "not be modified" in {
    val inputStream = new InputStream {
      override def read(): Int = 'c'.toInt
    }
    assert(JLine3.decodeInput(decoder, inputStream) == 'c'.toInt)
  }
  "swedish characters" should "be handled" in {
    val bytes = new LinkedBlockingQueue[Int]
    // these are the utf-8 codes for an umlauted a in swedish
    Seq(195, 164).foreach(b => bytes.put(b))
    val inputStream = new InputStream {
      override def read(): Int = Option(bytes.poll).getOrElse(-1)
    }
    assert(JLine3.decodeInput(decoder, inputStream) == 228)
  }
  "emoji" should "be handled" in {
    val bytes = new LinkedBlockingQueue[Int]
    // laughing and crying emoji in utf8
    Seq(0xF0, 0x9F, 0x98, 0x82).foreach(b => bytes.put(b))
    val inputStream = new InputStream {
      override def read(): Int = Option(bytes.poll).getOrElse(-1)
    }
    assert(JLine3.decodeInput(decoder, inputStream) == 128514)
  }
}
