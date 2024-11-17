/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import org.scalacheck._
import Gen.listOf
import Prop._
import Tags._

object TagsTest extends Properties("Tags") {
  final case class Size(value: Int)

  def tagMap: Gen[TagMap] = for (ts <- listOf(tagAndFrequency)) yield ts.toMap
  def tagAndFrequency: Gen[(Tag, Int)] =
    for (t <- tag; count <- Arbitrary.arbitrary[Int]) yield (t, count)
  def tag: Gen[Tag] = for (s <- Gen.alphaStr if !s.isEmpty) yield Tag(s)
  def size: Gen[Size] =
    for (i <- Arbitrary.arbitrary[Int] if i != Int.MinValue) yield Size(math.abs(i))

  given aTagMap: Arbitrary[Map[Tag, Int]] = Arbitrary(tagMap)
  given aTagAndFrequency: Arbitrary[(Tag, Int)] = Arbitrary(tagAndFrequency)
  given aTag: Arbitrary[Tag] = Arbitrary(tag)
  given aSize: Arbitrary[Size] = Arbitrary(size)

  property("exclusive allows all groups without the exclusive tag") = forAll {
    (tm: TagMap, tag: Tag) =>
      excl(tag)(tm - tag)
  }

  property("exclusive only allows a group with an excusive tag when the size is one") = forAll {
    (tm: TagMap, size: Size, etag: Tag) =>
      val absSize = size.value
      val tm2: TagMap =
        tm.updated(etag, absSize).updated(Tags.All, tm.getOrElse(Tags.All, 0) + absSize)
      (s"TagMap: $tm2") |:
        (excl(etag)(tm2) == (absSize <= 1))
  }

  property("exclusive always allows a group of size one") = forAll { (etag: Tag, mapTag: Tag) =>
    val tm: TagMap = Map(mapTag -> 1, Tags.All -> 1)
    excl(etag)(tm)
  }

  private def excl(tag: Tag): TagMap => Boolean = predicate(exclusive(tag) :: Nil)

}
