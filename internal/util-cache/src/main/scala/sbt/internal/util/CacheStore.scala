package sbt.internal.util

import sjsonnew.{ IsoString, JsonReader, JsonWriter, SupportConverter }

import java.io.{ File, InputStream, OutputStream }

import sbt.io.{ IO, Using }
import sbt.io.syntax.fileToRichFile

/**
 * A `CacheStore` is used by the caching infrastructure to persist cached information.
 */
trait CacheStore extends Input with Output {
  /** Delete the persisted information. */
  def delete(): Unit
}

/**
 * Factory that can derive new stores.
 */
trait CacheStoreFactory {
  /** Create a new store. */
  def derive(identifier: String): CacheStore

  /** Create a new `CacheStoreFactory` from this factory. */
  def sub(identifier: String): CacheStoreFactory
}

/**
 * A factory that creates new stores persisted in `base`.
 */
class DirectoryStoreFactory[J: IsoString](base: File, converter: SupportConverter[J]) extends CacheStoreFactory {

  IO.createDirectory(base)

  override def derive(identifier: String): CacheStore =
    new FileBasedStore(base / identifier, converter)

  override def sub(identifier: String): CacheStoreFactory =
    new DirectoryStoreFactory(base / identifier, converter)
}

/**
 * A `CacheStore` that persists information in `file`.
 */
class FileBasedStore[J: IsoString](file: File, converter: SupportConverter[J]) extends CacheStore {

  IO.touch(file, setModified = false)

  override def delete(): Unit =
    IO.delete(file)

  override def read[T: JsonReader](): T =
    Using.fileInputStream(file) { stream =>
      val input = new PlainInput(stream, converter)
      input.read()
    }

  override def read[T: JsonReader](default: => T): T =
    try read[T]()
    catch { case _: Exception => default }

  override def write[T: JsonWriter](value: T): Unit =
    Using.fileOutputStream(append = false)(file) { stream =>
      val output = new PlainOutput(stream, converter)
      output.write(value)
    }

  override def close(): Unit = ()

}

/**
 * A store that reads from `inputStream` and writes to `outputStream
 */
class StreamBasedStore[J: IsoString](inputStream: InputStream, outputStream: OutputStream, converter: SupportConverter[J]) extends CacheStore {

  override def delete(): Unit = ()

  override def read[T: JsonReader](): T = {
    val input = new PlainInput(inputStream, converter)
    input.read()
  }

  override def read[T: JsonReader](default: => T): T =
    try read[T]()
    catch { case _: Exception => default }

  override def write[T: JsonWriter](value: T): Unit = {
    val output = new PlainOutput(outputStream, converter)
    output.write(value)
  }

  override def close(): Unit = {
    inputStream.close()
    outputStream.close()
  }

}