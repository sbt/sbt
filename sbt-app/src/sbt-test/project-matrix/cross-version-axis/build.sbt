lazy val check = taskKey[Unit]("")

// a version on its own is the binary axis
lazy val bin = (projectMatrix in file("bin"))
  .addPlatforms(VirtualAxis.jvm)("2.13.18")(
    Seq(check := assert(crossVersion.value != CrossVersion.full, "bin"))
  )

// CrossVersion.full is the axis of the whole version, and the row asks for it in its settings
lazy val full = (projectMatrix in file("full"))
  .addPlatforms(VirtualAxis.jvm)(CrossVersion.full, "2.13.18")(
    Seq(check := assert(crossVersion.value == CrossVersion.full, "full"))
  )

// a cross version gives its rows the suffix it publishes them under
lazy val patch = (projectMatrix in file("patch"))
  .addPlatforms(VirtualAxis.jvm)(CrossVersion.patch, "2.13.18")

lazy val fixed = (projectMatrix in file("fixed"))
  .addPlatforms(VirtualAxis.jvm)(CrossVersion.constant("foo").axes("2.13.18"))

// an axis a build builds itself doesn't ask for a cross version
lazy val part = (projectMatrix in file("part"))
  .addPlatforms(VirtualAxis.jvm)(CrossVersionAxes(VirtualAxis.scalaPartialVersion("2.13.18")))

// a version, or several, give binary axes on their own; the matrix's own default axis is 3.x,
// so its row doesn't take a suffix
lazy val one = (projectMatrix in file("one")).addPlatforms(VirtualAxis.jvm)("2.13.18")
lazy val many = (projectMatrix in file("many"))
  .addPlatforms(VirtualAxis.jvm)(Seq("2.13.18", "3.3.6"))

// axes a build built itself
lazy val mixed = (projectMatrix in file("mixed")).addPlatforms(VirtualAxis.jvm)(
  Seq(VirtualAxis.scalaABIVersion("2.13.18"), VirtualAxis.scalaPartialVersion("3.3.6"))
)

// each factory says what the row takes as its value
lazy val abi = (projectMatrix in file("abi"))
  .addPlatforms(VirtualAxis.jvm)(CrossVersionAxes.abi("2.13.18"))
lazy val partial = (projectMatrix in file("partial"))
  .addPlatforms(VirtualAxis.jvm)(CrossVersionAxes.partial("3.3.6"))
lazy val whole = (projectMatrix in file("whole"))
  .addPlatforms(VirtualAxis.jvm)(CrossVersionAxes.full("2.13.18"))
lazy val byValue = (projectMatrix in file("byValue"))
  .addPlatforms(VirtualAxis.jvm)(CrossVersionAxes.by(_.take(1))("2.13.18"))

// the call doesn't give a version: one row, and it doesn't take a Scala library
lazy val java = (projectMatrix in file("java")).addPlatforms(VirtualAxis.jvm)(
  CrossVersionAxes()
)(Seq(check := assert(!autoScalaLibrary.value, "java")))

lazy val root = (project in file("."))
  .settings(
    check := {
      val matrices = Seq(bin, full, patch, fixed, part, one, many, mixed, abi, partial, whole,
        byValue, java)
      val ids = matrices.flatMap(_.allProjects().map(_._1.id))
      val named = Seq("bin2_13", "full2_13_18", "patch2_13_18", "fixedfoo", "part2_13", "one2_13",
        "many2_13", "many", "mixed2_13", "mixed3_3", "abi2_13", "partial3_3", "whole2_13_18",
        "byValue2", "java")
      assert(ids == named, s"rows: $ids")
    },
  )
