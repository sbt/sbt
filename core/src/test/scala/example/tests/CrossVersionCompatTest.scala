package example.tests

import sbt.librarymanagement.{ CrossVersion, Disabled }
import verify.BasicTestSuite
import scala.annotation.nowarn

@nowarn
object CrossVersionCompatTest extends BasicTestSuite {
  test("CrossVersion.Disabled is typed to be Disabled") {
    assert(CrossVersion.Disabled match {
      case _: Disabled => true
      case _           => false
    })
  }

  test("CrossVersion.Disabled functions as disabled") {
    assert(CrossVersion(CrossVersion.disabled, "1.0.0", "1.0") == None)
    assert(CrossVersion(CrossVersion.Disabled, "1.0.0", "1.0") == None)
  }

  test("CrossVersion.Disabled() is typed to be Disabled") {
    assert(CrossVersion.Disabled() match {
      case _: Disabled => true
      case _           => false
    })
  }

  test("CrossVersion.Disabled() functions as disabled") {
    assert(CrossVersion(CrossVersion.disabled, "1.0.0", "1.0") == None)
    assert(CrossVersion(CrossVersion.Disabled(), "1.0.0", "1.0") == None)
  }

  test("CrossVersion.Disabled is stable") {
    assert(CrossVersion.Disabled match {
      case CrossVersion.Disabled => true
      case _                     => false
    })
  }

  test("sbt.librarymanagement.Disabled is typed to be Disabled") {
    assert(Disabled match {
      case _: Disabled => true
      case _           => false
    })
  }

  test("sbt.librarymanagement.Disabled is stable") {
    assert(Disabled match {
      case Disabled => true
      case _        => false
    })
  }

  test("sbt.librarymanagement.Disabled() is typed to be Disabled") {
    assert(Disabled() match {
      case _: Disabled => true
      case _           => false
    })
  }

  test("CrossVersion.disabled is sbt.librarymanagement.Disabled") {
    assert(CrossVersion.disabled == Disabled)
  }

  test("CrossVersion.Disabled is sbt.librarymanagement.Disabled") {
    assert(CrossVersion.Disabled == Disabled)
  }

  test("CrossVersion.Disabled() is sbt.librarymanagement.Disabled") {
    assert(CrossVersion.Disabled() == Disabled)
  }
}
