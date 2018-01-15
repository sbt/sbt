package sbt.internal.librarymanagement

import sbt.librarymanagement._

// This is a specification to check the inconsistent duplicate warnings
class InconsistentDuplicateSpec extends UnitSpec {
  "Duplicate with different version" should "be warned" in {
    IvySbt.inconsistentDuplicateWarning(Seq(akkaActor214, akkaActor230)) shouldBe
      List(
        "Multiple dependencies with the same organization/name but different versions. To avoid conflict, pick one version:",
        " * com.typesafe.akka:akka-actor:(2.1.4, 2.3.0)"
      )
  }

  it should "not be warned if in different configurations" in {
    IvySbt.inconsistentDuplicateWarning(Seq(akkaActor214, akkaActor230Test)) shouldBe Nil
  }

  "Duplicate with same version" should "not be warned" in {
    IvySbt.inconsistentDuplicateWarning(Seq(akkaActor230Test, akkaActor230)) shouldBe Nil
  }

  def akkaActor214 =
    ModuleID("com.typesafe.akka", "akka-actor", "2.1.4").withConfigurations(Some("compile")) cross CrossVersion.binary
  def akkaActor230 =
    ModuleID("com.typesafe.akka", "akka-actor", "2.3.0").withConfigurations(Some("compile")) cross CrossVersion.binary
  def akkaActor230Test =
    ModuleID("com.typesafe.akka", "akka-actor", "2.3.0").withConfigurations(Some("test")) cross CrossVersion.binary
}
