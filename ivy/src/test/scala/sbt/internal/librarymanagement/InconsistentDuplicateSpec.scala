package sbt.internal.librarymanagement

import sbt.librarymanagement._
import verify.BasicTestSuite

// This is a specification to check the inconsistent duplicate warnings
object InconsistentDuplicateSpec extends BasicTestSuite {
  test("Duplicate with different version should be warned") {
    assert(
      IvySbt.inconsistentDuplicateWarning(Seq(akkaActor214, akkaActor230)) ==
        List(
          "Multiple dependencies with the same organization/name but different versions. To avoid conflict, pick one version:",
          " * com.typesafe.akka:akka-actor:(2.1.4, 2.3.0)"
        )
    )
  }

  test("it should not be warned if in different configurations") {
    assert(IvySbt.inconsistentDuplicateWarning(Seq(akkaActor214, akkaActor230Test)) == Nil)
  }

  test("Duplicate with same version should not be warned") {
    assert(IvySbt.inconsistentDuplicateWarning(Seq(akkaActor230Test, akkaActor230)) == Nil)
  }

  def akkaActor214 =
    ModuleID("com.typesafe.akka", "akka-actor", "2.1.4").withConfigurations(
      Some("compile")
    ) cross CrossVersion.binary
  def akkaActor230 =
    ModuleID("com.typesafe.akka", "akka-actor", "2.3.0").withConfigurations(
      Some("compile")
    ) cross CrossVersion.binary
  def akkaActor230Test =
    ModuleID("com.typesafe.akka", "akka-actor", "2.3.0").withConfigurations(
      Some("test")
    ) cross CrossVersion.binary
}
