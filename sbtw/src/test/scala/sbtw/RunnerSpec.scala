package sbtw

object RunnerSpec extends verify.BasicTestSuite:
  test("minimumJdkVersion should require JDK 17 for sbt 2.x") {
    assert(Runner.minimumJdkVersion(Some("2.0.0-RC9")) == 17)
  }

  test("minimumJdkVersion should require JDK 17 for sbt 2.x snapshot") {
    assert(Runner.minimumJdkVersion(Some("2.0.0-SNAPSHOT")) == 17)
  }

  test("minimumJdkVersion should require JDK 8 for sbt 1.x") {
    assert(Runner.minimumJdkVersion(Some("1.10.7")) == 8)
  }

  test("minimumJdkVersion should require JDK 8 when version is absent") {
    assert(Runner.minimumJdkVersion(None) == 8)
  }

  test("minimumJdkVersion should require JDK 17 for future sbt 3.x") {
    assert(Runner.minimumJdkVersion(Some("3.0.0")) == 17)
  }
end RunnerSpec
