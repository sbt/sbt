package sbt.internal

import java.nio.file.{ Path, Paths }

import org.scalatest.FlatSpec
import sbt.nio.FileStamp
import sbt.nio.FileStamp._
import sjsonnew.support.scalajson.unsafe.Converter

class FileStampJsonSpec extends FlatSpec {
  "file hashes" should "be serializable" in {
    val hashes = Seq(
      Paths.get("foo") -> FileStamp.hash("bar"),
      Paths.get("bar") -> FileStamp.hash("buzz")
    )
    val json = Converter.toJsonUnsafe(hashes)(fileHashJsonFormatter)
    val deserialized = Converter.fromJsonUnsafe(json)(fileHashJsonFormatter)
    assert(hashes == deserialized)
  }
  "file last modified times" should "be serializable" in {
    val lastModifiedTimes = Seq(
      Paths.get("foo") -> FileStamp.LastModified(1234),
      Paths.get("bar") -> FileStamp.LastModified(5678)
    )
    val json = Converter.toJsonUnsafe(lastModifiedTimes)(fileLastModifiedJsonFormatter)
    val deserialized = Converter.fromJsonUnsafe(json)(fileLastModifiedJsonFormatter)
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
    val json = Converter.toJsonUnsafe(both)(fileStampJsonFormatter)
    val deserialized = Converter.fromJsonUnsafe(json)(fileStampJsonFormatter)
    assert(both.sameElements(deserialized))
  }
}
