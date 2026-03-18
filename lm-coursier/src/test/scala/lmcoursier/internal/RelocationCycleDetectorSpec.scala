package lmcoursier.internal

import coursier.Resolution
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class RelocationCycleDetectorSpec extends AnyFunSuite with Matchers:

  test("incomplete resolution is not treated as having a relocation cycle"):
    RelocationCycleDetector.hasRelocationCycle(Resolution()) shouldBe false
