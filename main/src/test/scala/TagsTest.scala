package sbt

import org.scalacheck._
import Gen.{genInt,listOf,genString}
import Prop.forAll
import Tags._

object TagsTest extends Properties("Tags")
{
	def tagMap: Gen[TagMap] = for(ts <- listOf(tagAndFrequency)) yield ts.toMap
	def tagAndFrequency: Gen[(Tag, Int)] = for(t <- tag; count <- genInt) yield (t, count)
	def tag: Gen[Tag] = for(s <- genString) yield Tag(s)

	property("exclusive allows all groups without the exclusive tag") = forAll { (tm: TagMap, tag: Tag) =>
		excl(tag)(tm - tag)
	}
	property("exclusive only allows a group with an excusive tag when the size is one") = forAll { (tm: TagMap, size: Int, etag: Tag) =>
		val tm: TagMap = tm.updated(etag, math.abs(size))
		excl(etag)(tm) == (size <= 1)
	}
	property("exclusive always allows a group of size one") = forAll { (etag: Tag, mapTag: Tag) =>
		val tm: TagMap = Map(mapTag -> 1)
		excl(etag)(tm)
	}

	private[this] def excl(tag: Tag): TagMap => Boolean = predicate(exclusive(tag) :: Nil)

}