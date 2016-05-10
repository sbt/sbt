package coursier.util

import scala.collection.mutable.ArrayBuilder

/**
 * Base64 encoder
 * @author Mark Lister
 *         This software is distributed under the 2-Clause BSD license. See the
 *         LICENSE file in the root of the repository.
 *
 *         Copyright (c) 2014 - 2015 Mark Lister
 *
 *         The repo for this Base64 encoder lives at  https://github.com/marklister/base64
 *         Please send your issues, suggestions and pull requests there.
 */

object Base64 {

  case class B64Scheme(encodeTable: Array[Char], strictPadding: Boolean = true,
                       postEncode: String => String = identity,
                       preDecode: String => String = identity) {
    lazy val decodeTable = {
      val b: Array[Int] = new Array[Int](256)
      for (x <- encodeTable.zipWithIndex) {
        b(x._1) = x._2.toInt
      }
      b
    }
  }

  val base64 = new B64Scheme((('A' to 'Z') ++ ('a' to 'z') ++ ('0' to '9') ++ Seq('+', '/')).toArray)
  val base64Url = new B64Scheme(base64.encodeTable.dropRight(2) ++ Seq('-', '_'), false,
    _.replaceAllLiterally("=", "%3D"),
    _.replaceAllLiterally("%3D", "="))

  implicit class SeqEncoder(s: Seq[Byte]) {
    def toBase64(implicit scheme: B64Scheme = base64): String = Encoder(s.toArray).toBase64
  }

  implicit class Encoder(b: Array[Byte]) {
    val r = new StringBuilder((b.length + 3) * 4 / 3)
    lazy val pad = (3 - b.length % 3) % 3

    def toBase64(implicit scheme: B64Scheme = base64): String = {
      def sixBits(x: Byte, y: Byte, z: Byte): Unit = {
        val zz = (x & 0xff) << 16 | (y & 0xff) << 8 | (z & 0xff)
        r += scheme.encodeTable(zz >> 18)
        r += scheme.encodeTable(zz >> 12 & 0x3f)
        r += scheme.encodeTable(zz >> 6 & 0x3f)
        r += scheme.encodeTable(zz & 0x3f)
      }
      for (p <- 0 until b.length - 2 by 3) {
        sixBits(b(p), b(p + 1), b(p + 2))
      }
      pad match {
        case 0 =>
        case 1 => sixBits(b(b.length - 2), b(b.length - 1), 0)
        case 2 => sixBits(b(b.length - 1), 0, 0)
      }
      r.length = (r.length - pad)
      r ++= "=" * pad
      scheme.postEncode(r.toString())
    }
  }

  implicit class Decoder(s: String) {

    def toByteArray(implicit scheme: B64Scheme = base64): Array[Byte] = {
      val pre = scheme.preDecode(s)
      val cleanS = pre.replaceAll("=+$", "")
      val pad = pre.length - cleanS.length
      val computedPad = (4 - (cleanS.length % 4)) % 4
      val r = new ArrayBuilder.ofByte

      def threeBytes(a: Int, b: Int, c: Int, d: Int): Unit = {
        val i = a << 18 | b << 12 | c << 6 | d
        r += ((i >> 16).toByte)
        r += ((i >> 8).toByte)
        r += (i.toByte)
      }
      if (scheme.strictPadding) {
        if (pad > 2) throw new java.lang.IllegalArgumentException("Invalid Base64 String: (excessive padding) " + s)
        if (s.length % 4 != 0) throw new java.lang.IllegalArgumentException("Invalid Base64 String: (padding problem) " + s)
      }
      if (computedPad == 3) throw new java.lang.IllegalArgumentException("Invalid Base64 String: (string length) " + s)
      try {
        val s = (cleanS + "A" * computedPad)
        for (x <- 0 until s.length - 1 by 4) {
          val i = scheme.decodeTable(s.charAt(x)) << 18 |
            scheme.decodeTable(s.charAt(x + 1)) << 12 |
            scheme.decodeTable(s.charAt(x + 2)) << 6 |
            scheme.decodeTable(s.charAt(x + 3))
          r += ((i >> 16).toByte)
          r += ((i >> 8).toByte)
          r += (i.toByte)
        }
      } catch {
        case e: NoSuchElementException => throw new java.lang.IllegalArgumentException("Invalid Base64 String: (invalid character)" + e.getMessage + s)
      }
      val res = r.result
      res.slice(0, res.length - computedPad)
    }

  }

}
