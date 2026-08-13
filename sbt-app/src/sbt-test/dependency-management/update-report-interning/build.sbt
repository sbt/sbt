ThisBuild / scalaVersion := "2.13.16"

lazy val checkSharedAcrossProjects = taskKey[Unit]("Validates cross-project module report sharing")

// Two projects resolving the same coordinate independently. Coursier memoizes moduleReport on a key
// that includes the dependees, so each project builds its own instance; they become value-equal only
// once `update` strips the callers, which is where interning collapses them.
lazy val a = project.settings(
  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "2.4.0"
)

lazy val b = project.settings(
  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "2.4.0"
)

lazy val root = (project in file("."))
  .aggregate(a, b)
  .settings(
    checkSharedAcrossProjects := {
      def xml(ur: UpdateReport) =
        ur.configurations
          .find(_.configuration.name == "compile")
          .getOrElse(sys.error("no compile configuration"))
          .modules
          .find(_.module.name.startsWith("scala-xml"))
          .getOrElse(sys.error("scala-xml not in the report"))
      val fromA = xml((a / update).value)
      val fromB = xml((b / update).value)
      require(fromA == fromB, s"expected value-equal reports, got\n$fromA\nand\n$fromB")
      require(
        fromA.callers.isEmpty,
        s"update should have stripped the callers, got ${fromA.callers}"
      )
      require(
        fromA eq fromB,
        "two projects resolving one coordinate must share a single ModuleReport instance"
      )
      require(
        fromA.module eq fromB.module,
        "the ModuleID inside must be shared too"
      )
    }
  )
