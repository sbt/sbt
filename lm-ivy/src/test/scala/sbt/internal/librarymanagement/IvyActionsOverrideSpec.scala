package sbt.internal.librarymanagement

import verify.BasicTestSuite

object IvyActionsOverrideSpec extends BasicTestSuite:

  test(
    "IvyActions.applyDependencyOverrides should replace rev attribute for matching dependencies"
  ):
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
    assert(slf4jDep.isDefined)
    assert((slf4jDep.get \ "@rev").text == "2.0.16")
    assert((slf4jDep.get \ "@name").text == "slf4j-api")
    assert((slf4jDep.get \ "@conf").text == "compile->default(compile)")

    // Check other-lib is unchanged
    val otherDep = dependencies.find(d => (d \ "@org").text == "other.org")
    assert(otherDep.isDefined)
    assert((otherDep.get \ "@rev").text == "1.0.0")
    assert((otherDep.get \ "@name").text == "other-lib")

  test(
    "IvyActions.applyDependencyOverrides should preserve all attributes when replacing rev"
  ):
    val overrideMap = Map(("org.example", "test-lib") -> "3.0.0")

    val sampleXml =
      <ivy-module version="2.0">
        <dependencies>
          <dependency org="org.example" name="test-lib" rev="1.0.0" conf="compile->default" transitive="false" force="true"/>
        </dependencies>
      </ivy-module>

    val updated = IvyActions.applyDependencyOverrides(sampleXml, overrideMap)

    val dep = (updated \\ "dependency").head
    assert((dep \ "@org").text == "org.example")
    assert((dep \ "@name").text == "test-lib")
    assert((dep \ "@rev").text == "3.0.0")
    assert((dep \ "@conf").text == "compile->default")
    assert((dep \ "@transitive").text == "false")
    assert((dep \ "@force").text == "true")

  test("IvyActions.applyDependencyOverrides should not modify dependencies without overrides"):
    val overrideMap = Map(("org.other", "other-lib") -> "2.0.0")

    val sampleXml =
      <ivy-module version="2.0">
        <dependencies>
          <dependency org="org.example" name="test-lib" rev="1.0.0" conf="compile->default"/>
        </dependencies>
      </ivy-module>

    val updated = IvyActions.applyDependencyOverrides(sampleXml, overrideMap)

    val dep = (updated \\ "dependency").head
    assert((dep \ "@rev").text == "1.0.0") // Should remain unchanged
end IvyActionsOverrideSpec
