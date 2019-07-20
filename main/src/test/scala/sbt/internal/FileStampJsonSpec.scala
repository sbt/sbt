/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.nio.file.{ Path, Paths }

import org.scalatest.FlatSpec
import sbt.Scope
import sbt.nio.FileStamp
import sbt.nio.FileStamp.Formats
import sjsonnew.support.scalajson.unsafe.Converter

class FileStampJsonSpec extends FlatSpec {
  "file hashes" should "be serializable" in {
    val hashes = Seq(
      Paths.get("foo") -> FileStamp.hash("bar"),
      Paths.get("bar") -> FileStamp.hash("buzz")
    )
    import Formats.fileHashJsonFormatter
    val json = Converter.toJsonUnsafe(hashes)
    val deserialized = Converter.fromJsonUnsafe(json)
    assert(hashes == deserialized)
  }
  "file last modified times" should "be serializable" in {
    val lastModifiedTimes = Seq(
      Paths.get("foo") -> FileStamp.LastModified(1234),
      Paths.get("bar") -> FileStamp.LastModified(5678)
    )
    import Formats.fileLastModifiedJsonFormatter
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
    import Formats.fileStampJsonFormatter
    val json = Converter.toJsonUnsafe(both)
    val deserialized = Converter.fromJsonUnsafe(json)
    assert(both == deserialized)
  }
  "maps" should "be serializable" in {
    val hashes = Seq(
      Paths.get("foo") -> (FileStamp.hash("bar"): FileStamp),
      Paths.get("bar") -> (FileStamp.hash("buzz"): FileStamp)
    )
    val scope = Scope.Global.in(sbt.Keys.compile.key).toString
    import Formats.fileStampJsonFormatter
    import sjsonnew.BasicJsonProtocol._
    val map = Map(scope -> hashes)
    val json = Converter.toJsonUnsafe(map)
    val deserialized = Converter.fromJsonUnsafe[Map[String, Seq[(Path, FileStamp)]]](json)
    assert(map == deserialized)
  }
}
