package sbt.librarymanagement

import sbt.internal.util.UnitSpec

class UpdateOptionsSpec extends UnitSpec {

  "UpdateOptions" should "have proper toString defined" in {
    UpdateOptions().toString() should be(
      """|UpdateOptions(
        |  circularDependencyLevel = warn,
        |  latestSnapshots = false,
        |  consolidatedResolution = false,
        |  cachedResolution = false
        |)""".stripMargin)

    UpdateOptions()
      .withCircularDependencyLevel(CircularDependencyLevel.Error)
      .withCachedResolution(true)
      .withLatestSnapshots(true).toString() should be(
      """|UpdateOptions(
        |  circularDependencyLevel = error,
        |  latestSnapshots = true,
        |  consolidatedResolution = false,
        |  cachedResolution = true
        |)""".stripMargin)
  }
}
