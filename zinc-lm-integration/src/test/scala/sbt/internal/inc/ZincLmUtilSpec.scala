/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.inc

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ZincLmUtilSpec extends AnyFlatSpec with Matchers {
  "getDefaultBridgeSourceModule" should "use the passed scala organization for Scala 3 bridge modules" in {
    val customOrg = "example.scala.org"
    val module = ZincLmUtil.getDefaultBridgeSourceModule("3.3.5", customOrg)

    module.organization shouldBe customOrg
    module.name shouldBe "scala3-sbt-bridge"
  }
}
