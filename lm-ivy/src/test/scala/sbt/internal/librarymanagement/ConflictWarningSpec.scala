package sbt.internal.librarymanagement

import sbt.librarymanagement.*
import sbt.librarymanagement.syntax.*

object ConflictWarningSpec extends BaseIvySpecification {

  test("it should print out message about the cross-Scala conflict") {
    var found = false
    val deps = Vector(
      `scala2.13.6`,
      `cats-effect3.1.1`,
      `cats-core2.6.1`.cross(CrossVersion.for3Use2_13),
    )
    val m = module(defaultModuleId, deps, Some("3.0.1-RC2"))
    val report = ivyUpdate(m)
    val w = ConflictWarning.default("foo")

    try {
      ConflictWarning(w, report, log)
    } catch {
      case e: Throwable =>
        found = true
        assert(
          e.getMessage.linesIterator.toList.head
            .startsWith("Conflicting cross-version suffixes in")
        )
    }
    if (!found) {
      sys.error("conflict warning was expected, but didn't happen sbt/sbt#6578")
    }
  }

  lazy val `scala2.13.6` =
    ModuleID("org.scala-lang", "scala-library", "2.13.6").withConfigurations(Some("compile"))
  lazy val `cats-effect3.1.1` =
    ("org.typelevel" %% "cats-effect" % "3.1.1").withConfigurations(Some("compile"))
  lazy val `cats-core2.6.1` =
    ("org.typelevel" %% "cats-core" % "2.6.1").withConfigurations(Some("compile"))
}
