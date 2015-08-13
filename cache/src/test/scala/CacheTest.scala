package sbt

import java.io.File
import Types.:+:

object CacheTest // extends Properties("Cache test")
{
  val lengthCache = new File("/tmp/length-cache")
  val cCache = new File("/tmp/c-cache")

  import Cache._
  import FileInfo.hash._
  import Ordering._
  import sbinary.DefaultProtocol.FileFormat
  def test(): Unit = {
    lazy val create = new File("test")

    val length = cached(lengthCache) {
      (f: File) => { println("File length: " + f.length); f.length }
    }

    lazy val fileLength = length(create)

    val c = cached(cCache) { (in: (File :+: Long :+: HNil)) =>
      val file :+: len :+: HNil = in
      println("File: " + file + " (" + file.exists + "), length: " + len)
      (len + 1) :+: file :+: HNil
    }
    c(create :+: fileLength :+: HNil)
  }
}
