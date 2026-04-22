package lmcoursier

import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import sbt.librarymanagement.*

final class FromSbtPlatformSpec extends AnyPropSpec with Matchers {

  property("explicit platform should not be overridden by project platform") {
    // Test case for issue #8665: Auto-injected Scala library with explicit .platform(Platform.jvm)
    // should not get project platform applied

    val scala3Library = ModuleID("org.scala-lang", "scala3-library", "3.7.4")
      .withCrossVersion(Binary())
      .platform(Platform.jvm) // Explicit platform set to jvm

    val projectPlatform = Some("native0.5") // Project has native0.5 platform

    // When converting to Coursier module, the name should NOT include native0.5 suffix
    val (module, _) = FromSbt.moduleVersion(
      scala3Library,
      scalaVersion = "3.7.4",
      scalaBinaryVersion = "3",
      optionalCrossVer = false,
      projectPlatform = projectPlatform
    )

    // The module name should be scala3-library_3, NOT scala3-library_native0.5_3
    module.name.value shouldBe "scala3-library_3"
    module.name.value should not contain "native0.5"
  }

  property("project platform should apply to dependencies without explicit platform") {
    // Dependencies without explicit platform should get project platform
    val regularDep = ModuleID("com.example", "foo", "1.0.0")
      .withCrossVersion(Binary())
    // No explicit platform set

    val projectPlatform = Some("native0.5")

    val (module, _) = FromSbt.moduleVersion(
      regularDep,
      scalaVersion = "2.13.18",
      scalaBinaryVersion = "2.13",
      optionalCrossVer = false,
      projectPlatform = projectPlatform
    )

    // Should get the project platform suffix
    module.name.value shouldBe "foo_native0.5_2.13"
  }

  property("explicit platform should take priority over project platform") {
    // When a dependency has an explicit platform, it should be used even if project has a different platform
    val depWithExplicitPlatform = ModuleID("com.example", "bar", "1.0.0")
      .withCrossVersion(Binary())
      .platform("js1") // Explicit platform set to js1

    val projectPlatform = Some("native0.5") // Project has different platform

    val (module, _) = FromSbt.moduleVersion(
      depWithExplicitPlatform,
      scalaVersion = "2.13.18",
      scalaBinaryVersion = "2.13",
      optionalCrossVer = false,
      projectPlatform = projectPlatform
    )

    // Should use explicit platform (js1), not project platform (native0.5)
    module.name.value shouldBe "bar_js1_2.13"
    module.name.value should not contain "native0.5"
  }

  property("jvm platform should not add suffix") {
    // JVM platform (explicit or project) should not add suffix
    val dep = ModuleID("com.example", "baz", "1.0.0")
      .withCrossVersion(Binary())
      .platform(Platform.jvm)

    val (module, _) = FromSbt.moduleVersion(
      dep,
      scalaVersion = "2.13.18",
      scalaBinaryVersion = "2.13",
      optionalCrossVer = false,
      projectPlatform = Some("native0.5")
    )

    // JVM platform should not add suffix, even with project platform set
    module.name.value shouldBe "baz_2.13"
    module.name.value should not contain "jvm"
    module.name.value should not contain "native0.5"
  }

  property("no platform when both are None") {
    val dep = ModuleID("com.example", "qux", "1.0.0")
      .withCrossVersion(Binary())
    // No explicit platform

    val (module, _) = FromSbt.moduleVersion(
      dep,
      scalaVersion = "2.13.18",
      scalaBinaryVersion = "2.13",
      optionalCrossVer = false,
      projectPlatform = None
    )

    // Should just have cross-version suffix, no platform
    module.name.value shouldBe "qux_2.13"
  }

  property("issue #9117: project with native0.5 publishes as _native0.5_3") {
    val projectID = ModuleID("com.indoorvivants", "sniper", "0.0.9-SNAPSHOT")
      .withCrossVersion(Binary())
      .platform("native0.5")

    val (module, version) = FromSbt.moduleVersion(
      projectID,
      scalaVersion = "3.8.3",
      scalaBinaryVersion = "3",
      optionalCrossVer = false,
      projectPlatform = Some("native0.5")
    )

    module.name.value shouldBe "sniper_native0.5_3"
    version shouldBe "0.0.9-SNAPSHOT"
  }
}
