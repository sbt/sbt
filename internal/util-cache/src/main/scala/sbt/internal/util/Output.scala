package sbt.internal.util

import sbt.io.Using

import java.io.{ Closeable, OutputStream }

import scala.util.{ Failure, Success }

import sjsonnew.{ IsoString, JsonWriter, SupportConverter }

trait Output extends Closeable {
  def write[T: JsonWriter](value: T): Unit
}

class PlainOutput[J: IsoString](output: OutputStream, converter: SupportConverter[J]) extends Output {
  val isoFormat: IsoString[J] = implicitly
  override def write[T: JsonWriter](value: T): Unit = {
    converter.toJson(value) match {
      case Success(js) =>
        val asString = isoFormat.to(js)
        Using.bufferedOutputStream(output) { writer =>
          val out = new java.io.PrintWriter(writer)
          out.print(asString)
          out.flush()
        }
      case Failure(ex) =>
        throw ex
    }
  }

  override def close(): Unit = output.close()
}
