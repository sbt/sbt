lazy val check = taskKey[Unit]("")

// the axis carries the Scala version, so the row is given no `scalaVersions`
lazy val core = (projectMatrix in file("core"))
  .customRow(true, Seq(VirtualAxis.jvm, VirtualAxis.scalaABIVersion("3.9.0")), Nil)

lazy val root = (project in file("."))
  .settings(
    check := {
      val ids = core.allProjects().map(_._1.id)
      assert(ids == Seq(), s"rows: $ids")
    },
  )
