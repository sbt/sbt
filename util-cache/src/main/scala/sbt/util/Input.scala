package sbt.util

import java.io.{ Closeable, InputStream }
import scala.util.control.NonFatal
import sjsonnew.{ IsoString, JsonReader, SupportConverter }
import sbt.io.{ IO, Using }

trait Input extends Closeable {
  def read[T: JsonReader](): T
  def read[T: JsonReader](default: => T): T =
    try read[T]()
    catch { case NonFatal(_) => default }
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

  def read[T: JsonReader]() = converter.fromJson(isoFormat.from(readFully())).get

  def close() = input.close()
}
