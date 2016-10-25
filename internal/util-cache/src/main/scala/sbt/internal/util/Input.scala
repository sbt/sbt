package sbt.internal.util

import sbt.io.{ IO, Using }

import java.io.{ Closeable, InputStream }

import scala.util.{ Failure, Success }

import sjsonnew.{ IsoString, JsonReader, SupportConverter }

trait Input extends Closeable {
  def read[T: JsonReader](): T
  def read[T: JsonReader](default: => T): T
}

class PlainInput[J: IsoString](input: InputStream, converter: SupportConverter[J]) extends Input {
  val isoFormat: IsoString[J] = implicitly
  private def readFully(): String = {
    Using.streamReader(input, IO.utf8) { reader =>
      val builder = new StringBuilder()
      val bufferSize = 1024
      val buffer = new Array[Char](bufferSize)
      var read = 0
      while ({ read = reader.read(buffer, 0, bufferSize); read != -1 }) {
        builder.append(String.valueOf(buffer.take(read)))
      }
      builder.toString()
    }
  }

  override def read[T: JsonReader](): T = {
    val string = readFully()
    val json = isoFormat.from(string)
    converter.fromJson(json) match {
      case Success(value) => value
      case Failure(ex)    => throw ex
    }
  }

  override def read[T: JsonReader](default: => T): T =
    try read[T]()
    catch { case _: Exception => default }

  override def close(): Unit = input.close()
}
