package sbt.util

import java.io.{ File, InputStream, OutputStream }
import sbt.io.syntax.fileToRichFile
import sbt.io.{ IO, Using }
import sjsonnew.{ IsoString, JsonReader, JsonWriter, SupportConverter }
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter, Parser }
import sjsonnew.shaded.scalajson.ast.unsafe.JValue

/** A `CacheStore` is used by the caching infrastructure to persist cached information. */
abstract class CacheStore extends Input with Output {

  /** Delete the persisted information. */
  def delete(): Unit

}

object CacheStore {
  implicit lazy val jvalueIsoString: IsoString[JValue] =
    IsoString.iso(CompactPrinter.apply, Parser.parseUnsafe)

  /** Returns file-based CacheStore using standard JSON converter. */
  def apply(cacheFile: File): CacheStore = file(cacheFile)

  /** Returns file-based CacheStore using standard JSON converter. */
  def file(cacheFile: File): CacheStore = new FileBasedStore[JValue](cacheFile, Converter)
}

/** Factory that can make new stores. */
abstract class CacheStoreFactory {

  /** Create a new store. */
  def make(identifier: String): CacheStore

  /** Create a new `CacheStoreFactory` from this factory. */
  def sub(identifier: String): CacheStoreFactory

  /** A symbolic alias for `sub`. */
  final def /(identifier: String): CacheStoreFactory = sub(identifier)
}

object CacheStoreFactory {
  implicit lazy val jvalueIsoString: IsoString[JValue] =
    IsoString.iso(CompactPrinter.apply, Parser.parseUnsafe)

  /** Returns directory-based CacheStoreFactory using standard JSON converter. */
  def apply(base: File): CacheStoreFactory = directory(base)

  /** Returns directory-based CacheStoreFactory using standard JSON converter. */
  def directory(base: File): CacheStoreFactory = new DirectoryStoreFactory[JValue](base, Converter)
}

/** A factory that creates new stores persisted in `base`. */
class DirectoryStoreFactory[J: IsoString](base: File, converter: SupportConverter[J])
    extends CacheStoreFactory {
  IO.createDirectory(base)

  def make(identifier: String): CacheStore = new FileBasedStore(base / identifier, converter)

  def sub(identifier: String): CacheStoreFactory =
    new DirectoryStoreFactory(base / identifier, converter)
}

/** A `CacheStore` that persists information in `file`. */
class FileBasedStore[J: IsoString](file: File, converter: SupportConverter[J]) extends CacheStore {
  IO.touch(file, setModified = false)

  def read[T: JsonReader]() =
    Using.fileInputStream(file)(stream => new PlainInput(stream, converter).read())

  def write[T: JsonWriter](value: T) =
    Using.fileOutputStream(append = false)(file) { stream =>
      new PlainOutput(stream, converter).write(value)
    }

  def delete() = IO.delete(file)
  def close() = ()
}

/** A store that reads from `inputStream` and writes to `outputStream`. */
class StreamBasedStore[J: IsoString](
    inputStream: InputStream,
    outputStream: OutputStream,
    converter: SupportConverter[J]
) extends CacheStore {
  def read[T: JsonReader]() = new PlainInput(inputStream, converter).read()
  def write[T: JsonWriter](value: T) = new PlainOutput(outputStream, converter).write(value)
  def delete() = ()
  def close() = { inputStream.close(); outputStream.close() }
}
