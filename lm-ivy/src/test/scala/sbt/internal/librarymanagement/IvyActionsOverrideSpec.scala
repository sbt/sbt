package sbt.internal.librarymanagement

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IvyActionsOverrideSpec extends AnyFlatSpec with Matchers {

  "IvyActions.applyDependencyOverrides" should "replace rev attribute for matching dependencies" in {
    val overrideMap = Map(("org.slf4j", "slf4j-api") -> "2.0.16")

    val sampleXml =
      <ivy-module version="2.0">
        <info organisation="test" module="test" revision="1.0"/>
        <dependencies>
          <dependency org="org.slf4j" name="slf4j-api" rev="managed" conf="compile->default(compile)"/>
          <dependency org="other.org" name="other-lib" rev="1.0.0" conf="compile->default(compile)"/>
        </dependencies>
      </ivy-module>

    val updated = IvyActions.applyDependencyOverrides(sampleXml, overrideMap)

    val dependencies = (updated \\ "dependency")

    // Check slf4j-api has overridden version
    val slf4jDep = dependencies.find(d => (d \ "@org").text == "org.slf4j")
    slf4jDep shouldBe defined
    (slf4jDep.get \ "@rev").text shouldBe "2.0.16"
    (slf4jDep.get \ "@name").text shouldBe "slf4j-api"
    (slf4jDep.get \ "@conf").text shouldBe "compile->default(compile)"

    // Check other-lib is unchanged
    val otherDep = dependencies.find(d => (d \ "@org").text == "other.org")
    otherDep shouldBe defined
    (otherDep.get \ "@rev").text shouldBe "1.0.0"
    (otherDep.get \ "@name").text shouldBe "other-lib"
  }

  it should "preserve all attributes when replacing rev" in {
    val overrideMap = Map(("org.example", "test-lib") -> "3.0.0")

    val sampleXml =
      <ivy-module version="2.0">
        <dependencies>
          <dependency org="org.example" name="test-lib" rev="1.0.0" conf="compile->default" transitive="false" force="true"/>
        </dependencies>
      </ivy-module>

    val updated = IvyActions.applyDependencyOverrides(sampleXml, overrideMap)

    val dep = (updated \\ "dependency").head
    (dep \ "@org").text shouldBe "org.example"
    (dep \ "@name").text shouldBe "test-lib"
    (dep \ "@rev").text shouldBe "3.0.0"
    (dep \ "@conf").text shouldBe "compile->default"
    (dep \ "@transitive").text shouldBe "false"
    (dep \ "@force").text shouldBe "true"
  }

  it should "not modify dependencies without overrides" in {
    val overrideMap = Map(("org.other", "other-lib") -> "2.0.0")

    val sampleXml =
      <ivy-module version="2.0">
        <dependencies>
          <dependency org="org.example" name="test-lib" rev="1.0.0" conf="compile->default"/>
        </dependencies>
      </ivy-module>

    val updated = IvyActions.applyDependencyOverrides(sampleXml, overrideMap)

    val dep = (updated \\ "dependency").head
    (dep \ "@rev").text shouldBe "1.0.0" // Should remain unchanged
  }
}
