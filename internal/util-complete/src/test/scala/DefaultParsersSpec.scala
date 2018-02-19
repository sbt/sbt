/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package complete

import org.scalacheck._, Prop._

object DefaultParsersSpec extends Properties("DefaultParsers") {
  import DefaultParsers._

  property("validID == matches(ID, s)") = forAll((s: String) => validID(s) == matches(ID, s))
}
