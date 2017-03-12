package sbt

import org.specs2._

class InconsistentDuplicateSpec extends Specification {
  def is = s2"""

  This is a specification to check the inconsistent duplicate warnings

  Duplicate with different version should
    be warned                                                   $warn1
    not be warned if in different configurations                $nodupe2

  Duplicate with same version should
    not be warned                                               $nodupe1
                                                                """

  def akkaActor214 =
    ModuleID("com.typesafe.akka", "akka-actor", "2.1.4", Some("compile")) cross CrossVersion.binary
  def akkaActor230 =
    ModuleID("com.typesafe.akka", "akka-actor", "2.3.0", Some("compile")) cross CrossVersion.binary
  def akkaActor230Test =
    ModuleID("com.typesafe.akka", "akka-actor", "2.3.0", Some("test")) cross CrossVersion.binary

  def warn1 =
    IvySbt.inconsistentDuplicateWarning(Seq(akkaActor214, akkaActor230)) must_==
      List(
        "Multiple dependencies with the same organization/name but different versions. To avoid conflict, pick one version:",
        " * com.typesafe.akka:akka-actor:(2.1.4, 2.3.0)"
      )

  def nodupe1 =
    IvySbt.inconsistentDuplicateWarning(Seq(akkaActor230Test, akkaActor230)) must_== Nil

  def nodupe2 =
    IvySbt.inconsistentDuplicateWarning(Seq(akkaActor214, akkaActor230Test)) must_== Nil

}
