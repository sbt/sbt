/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package xsbt.api

import xsbti.SafeLazy
import xsbti.api._
import sbt.Using
import sbinary._
import DefaultProtocol._
import Operations.{ read, write }
import java.io.File
import scala.collection.mutable

object SourceFormat extends Format[Source] {
  import java.io._
  def reads(in: Input): Source =
    {
      val oin = new ObjectInputStream(new InputWrapperStream(in))
      try { oin.readObject.asInstanceOf[Source] } finally { oin.close() }
    }
  def writes(out: Output, src: Source): Unit = {
    val oout = new ObjectOutputStream(new OutputWrapperStream(out))
    try { oout.writeObject(src) } finally { oout.close() }
  }
}
final class InputWrapperStream(in: Input) extends java.io.InputStream {
  def toInt(b: Byte) = if (b < 0) b + 256 else b.toInt
  def read() = try { toInt(in.readByte) } catch { case e: sbinary.EOF => -1 }
  override def read(b: Array[Byte], off: Int, len: Int) = in.readTo(b, off, len)
}
final class OutputWrapperStream(out: Output) extends java.io.OutputStream {
  override def write(bs: Array[Byte], off: Int, len: Int) = out.writeAll(bs, off, len)
  def write(b: Int) = out.writeByte(b.toByte)
}
