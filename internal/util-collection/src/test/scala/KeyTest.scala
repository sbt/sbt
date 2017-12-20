/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

import org.scalacheck._
import Prop._

object KeyTest extends Properties("AttributeKey") {
  property("equality") = {
    compare(AttributeKey[Int]("test"), AttributeKey[Int]("test"), true) &&
    compare(AttributeKey[Int]("test"), AttributeKey[Int]("test", "description"), true) &&
    compare(AttributeKey[Int]("test", "a"), AttributeKey[Int]("test", "b"), true) &&
    compare(AttributeKey[Int]("test"), AttributeKey[Int]("tests"), false) &&
    compare(AttributeKey[Int]("test"), AttributeKey[Double]("test"), false) &&
    compare(AttributeKey[java.lang.Integer]("test"), AttributeKey[Int]("test"), false) &&
    compare(AttributeKey[Map[Int, String]]("test"), AttributeKey[Map[Int, String]]("test"), true) &&
    compare(AttributeKey[Map[Int, String]]("test"), AttributeKey[Map[Int, _]]("test"), false)
  }

  def compare(a: AttributeKey[_], b: AttributeKey[_], same: Boolean) =
    ("a.label: " + a.label) |:
      ("a.manifest: " + a.manifest) |:
      ("b.label: " + b.label) |:
      ("b.manifest: " + b.manifest) |:
      ("expected equal? " + same) |:
      compare0(a, b, same)

  def compare0(a: AttributeKey[_], b: AttributeKey[_], same: Boolean) =
    if (same) {
      ("equality" |: (a == b)) &&
      ("hash" |: (a.hashCode == b.hashCode))
    } else
      ("equality" |: (a != b))
}
