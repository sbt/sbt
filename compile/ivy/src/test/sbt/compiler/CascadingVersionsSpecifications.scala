package sbt
package compiler

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CascadingVersionsSpecification extends Specification {

  "Cascading version numbers" should {

    "be correctly generated from Scala release versions (X.Y.Z)" in {
      val version = "2.11.7"
      val cascadingVersions = ComponentCompiler.cascadingSourceModuleVersions(version)

      cascadingVersions === Seq("2.11.7", "2.11")
    }

    "be correctly generated from Scala RC, milestones, ... (X.Y.Z-...)" in {
      val version = "2.12.0-M1"
      val cascadingVersions = ComponentCompiler.cascadingSourceModuleVersions(version)

      cascadingVersions === Seq("2.12.0-M1", "2.12.0", "2.12")
    }

    "be correctly generated from custom Scala builds (X.Y.Z-...)" in {
      val version = "2.11.8-20150630-134856-c8fbc41631"
      val cascadingVersions = ComponentCompiler.cascadingSourceModuleVersions(version)

      cascadingVersions === Seq("2.11.8-20150630-134856-c8fbc41631", "2.11.8", "2.11")
    }

    "be correctly generated for unrecognizable versions" in {
      val version = "some unintelligible format"
      val cascadingVersions = ComponentCompiler.cascadingSourceModuleVersions(version)

      cascadingVersions === Seq(version)
    }

    "not recognize version numbers such as X.Y.Z-" in {
      val version = "2.11.7-"
      val cascadingVersions = ComponentCompiler.cascadingSourceModuleVersions(version)

      cascadingVersions === Seq(version)
    }

  }

}
