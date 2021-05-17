package example

import wvlet.airspec.AirSpec

class HelloAirSpec extends AirSpec {
  test("Run AirSpec assertion") {
    "hello" shouldBe "hello"
  }
}
