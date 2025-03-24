/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.io.{ File, InputStream, OutputStream }

import sbt.io.syntax.fileToRichFile
import sbt.io.IO
import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Parser }
import sjsonnew.{ IsoString, JsonReader, JsonWriter, SupportConverter }

/** A `CacheStore` is used by the caching infrastructure to persist cached information. */
abstract class CacheStore extends Input with Output {

  /** Delete the persisted information. */
  def delete(): Unit

}

object CacheStore {
  @deprecated("Create your own IsoString[JValue]", "1.4") given jvalueIsoString: IsoString[JValue] =
    IsoString.iso(CompactPrinter.apply, Parser.parseUnsafe)

  /** Returns file-based CacheStore using standard JSON converter. */
  def apply(cacheFile: File): CacheStore = file(cacheFile)

  /** Returns file-based CacheStore using standard JSON converter. */
  def file(cacheFile: File): CacheStore = new FileBasedStore(cacheFile)
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
  @deprecated("Create your own IsoString[JValue]", "1.4") given jvalueIsoString: IsoString[JValue] =
    IsoString.iso(CompactPrinter.apply, Parser.parseUnsafe)

  /** Returns directory-based CacheStoreFactory using standard JSON converter. */
  def apply(base: File): CacheStoreFactory = directory(base)

  /** Returns directory-based CacheStoreFactory using standard JSON converter. */
  def directory(base: File): CacheStoreFactory = new DirectoryStoreFactory(base)
}

/** A factory that creates new stores persisted in `base`. */
class DirectoryStoreFactory[J](base: File) extends CacheStoreFactory {
  IO.createDirectory(base)

  @deprecated("Use constructor without converter", "1.4")
  def this(base: File, converter: sjsonnew.SupportConverter[J])(using e: sjsonnew.IsoString[J]) =
    this(base)

  def make(identifier: String): CacheStore = new FileBasedStore(base / identifier)

  def sub(identifier: String): CacheStoreFactory =
    new DirectoryStoreFactory(base / identifier)
}

/** A `CacheStore` that persists information in `file`. */
class FileBasedStore[J](file: File) extends CacheStore {
  IO.touch(file, setModified = false)

  @deprecated("Use constructor without converter", "1.4")
  def this(file: File, converter: sjsonnew.SupportConverter[J])(using e: sjsonnew.IsoString[J]) =
    this(file)

  def read[T: JsonReader]() =
    new FileInput(file).read()

  def write[T: JsonWriter](value: T) =
    new FileOutput(file).write(value)

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
