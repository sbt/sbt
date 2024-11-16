/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.nio.file.{ Path, Paths }

import org.scalatest.flatspec.AnyFlatSpec
import sbt.nio.FileStamp
import sbt.nio.FileStamp.Formats
import sjsonnew.JsonFormat
import sjsonnew.support.scalajson.unsafe.Converter

class FileStampJsonSpec extends AnyFlatSpec {
  "file hashes" should "be serializable" in {
    val hashes = Seq(
      Paths.get("foo") -> FileStamp.hash("bar"),
      Paths.get("bar") -> FileStamp.hash("buzz")
    )
    given formatter: JsonFormat[Seq[(Path, FileStamp.Hash)]] =
      Formats.seqPathHashJsonFormatter
    val json = Converter.toJsonUnsafe(hashes)
    val deserialized = Converter.fromJsonUnsafe(json)
    assert(hashes == deserialized)
  }
  "file last modified times" should "be serializable" in {
    val lastModifiedTimes = Seq(
      Paths.get("foo") -> FileStamp.LastModified(1234),
      Paths.get("bar") -> FileStamp.LastModified(5678)
    )
    given formatter: JsonFormat[Seq[(Path, FileStamp.LastModified)]] =
      Formats.seqPathLastModifiedJsonFormatter
    val json = Converter.toJsonUnsafe(lastModifiedTimes)
    val deserialized = Converter.fromJsonUnsafe(json)
    assert(lastModifiedTimes == deserialized)
  }
  "both" should "be serializable" in {
    val hashes = Seq(
      Paths.get("foo") -> FileStamp.hash("bar"),
      Paths.get("bar") -> FileStamp.hash("buzz")
    )
    val lastModifiedTimes = Seq(
      Paths.get("foo") -> FileStamp.LastModified(1234),
      Paths.get("bar") -> FileStamp.LastModified(5678)
    )
    val both: Seq[(Path, FileStamp)] = hashes ++ lastModifiedTimes
    import Formats.seqPathFileStampJsonFormatter
    val json = Converter.toJsonUnsafe(both)
    val deserialized = Converter.fromJsonUnsafe(json)
    assert(both == deserialized)
  }
}
