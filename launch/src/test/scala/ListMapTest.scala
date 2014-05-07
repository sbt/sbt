package xsbt.boot

import org.scalacheck._

object ListMapProperties extends Properties("ListMap") {
  implicit val genListMap = Arbitrary(for (list <- Arbitrary.arbitrary[List[(Int, Int)]]) yield ListMap(list: _*))

  property("ListMap from List contains all members of that List") = Prop.forAll { (list: List[(Int, Int)]) =>
    val map = ListMap(list: _*)
    list forall { entry => map contains entry._1 }
  }
  property("contains added entry") = Prop.forAll { (map: ListMap[Int, Int], key: Int, value: Int) =>
    { (map + (key, value)) contains (key) } &&
      { (map + (key, value))(key) == value } &&
      { (map + (key, value)).get(key) == Some(value) }
  }
  property("remove") = Prop.forAll { (map: ListMap[Int, Int], key: Int) =>
    { Prop.throws(classOf[Exception])((map - key)(key)) } &&
      { !(map - key).contains(key) } &&
      { (map - key).get(key).isEmpty }
  }
  property("empty") = Prop.forAll { (key: Int) =>
    { Prop.throws(classOf[Exception])(ListMap.empty(key)) }
    { !ListMap.empty.contains(key) } &&
      { ListMap.empty.get(key).isEmpty }
  }
}

object ListMapEmpty extends Properties("ListMap.empty") {
  import ListMap.empty
  property("isEmpty") = empty.isEmpty
  property("toList.isEmpty") = empty.toList.isEmpty
  property("toSeq.isEmpty") = empty.toSeq.isEmpty
  property("toStream.isEmpty") = empty.toStream.isEmpty
  property("keys.isEmpty") = empty.keys.isEmpty
  property("iterator.isEmpty") = empty.iterator.isEmpty
}