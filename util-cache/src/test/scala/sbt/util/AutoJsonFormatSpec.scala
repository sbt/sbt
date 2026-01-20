/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sjsonnew.BasicJsonProtocol.given
import scala.util.{ Try, Failure }

class AutoJsonFormatSpec extends AnyFlatSpec with Matchers {

  "AutoJsonFormat" should "provide fallback format that throws with a helpful message" in {
    val format = AutoJsonFormat.fallbackFormat[String]("TestType")

    Try(format.write("test", null)) should matchPattern {
      case Failure(_: UnsupportedOperationException) =>
    }

    Try(format.read(None, null)) should matchPattern {
      case Failure(_: UnsupportedOperationException) =>
    }
  }

  it should "create case class format for simple case classes" in {
    case class TestData(name: String, age: Int, active: Boolean)

    given sjsonnew.JsonFormat[TestData] = AutoJsonFormat.derived
    val format = summon[sjsonnew.JsonFormat[TestData]]
    val testData = TestData("test", 42, true)

    format should not be null
    testData should not be null
  }

  it should "handle nested case classes" in {
    case class Address(street: String, city: String)
    case class Person(name: String, address: Address)

    given sjsonnew.JsonFormat[Address] = AutoJsonFormat.derived
    given sjsonnew.JsonFormat[Person] = AutoJsonFormat.derived
    val format = summon[sjsonnew.JsonFormat[Person]]
    val person = Person("John", Address("123 Main St", "Anytown"))

    format should not be null
    person should not be null
  }

  it should "handle optional fields" in {
    case class WithOptional(name: String, age: Option[Int])

    given sjsonnew.JsonFormat[WithOptional] = AutoJsonFormat.derived
    val format = summon[sjsonnew.JsonFormat[WithOptional]]
    val withSome = WithOptional("test", Some(42))
    val withNone = WithOptional("test", None)

    format should not be null
    withSome should not be null
    withNone should not be null
  }

  it should "include helpful error messages" in {
    val format = AutoJsonFormat.fallbackFormat[String]("TestType")

    val exception = intercept[UnsupportedOperationException] {
      format.write("test", null)
    }

    exception.getMessage should include("TestType")
    exception.getMessage should include("Def.uncached()")
    exception.getMessage should include("JsonFormat")
  }
}
