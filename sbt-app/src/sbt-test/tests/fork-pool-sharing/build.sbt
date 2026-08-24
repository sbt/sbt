// Five projects of four test classes each against a TWO-JVM pool, so the pool is scarce and the projects
// must compete for it. Each is a group that could fill the pool alone; the run is correct only if the two
// JVMs go to the first workers of two DIFFERENT projects, leaving no project holding both while another has
// none. Deliberately more projects than JVMs and more classes per project than JVMs: with a pool as wide as
// either number, both schedules look alike and the test would pass on any build.

ThisBuild / scalaVersion := "2.12.21"

Global / testForkedWorkStealing := true

// Replacing the Seq rather than appending, since += only tightens.
Global / concurrentRestrictions := Seq(
  Tags.limitAll(java.lang.Runtime.getRuntime.availableProcessors),
  Tags.limit(Tags.ForkedTestGroup, 2),
  Tags.exclusiveGroup(Tags.Clean)
)

val recordsDir = settingKey[File]("Where each forked JVM records the window it spent on a class")
ThisBuild / recordsDir := (ThisBuild / baseDirectory).value / "records"

lazy val commonSettings = Seq(
  libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test,
  Test / fork := true,
  // One class at a time per JVM, the integration-test posture: a project spreads by taking MORE JVMs, which
  // is exactly the competition under test.
  Test / testForkedParallel := false,
  Test / javaOptions ++= Seq(
    s"-Drecords.dir=${(ThisBuild / recordsDir).value}",
    s"-Dproj=${name.value}"
  )
)

lazy val a = project.settings(commonSettings)
lazy val b = project.settings(commonSettings)
lazy val c = project.settings(commonSettings)
lazy val d = project.settings(commonSettings)
lazy val e = project.settings(commonSettings)

val check = taskKey[Unit]("the pool is shared BETWEEN projects, not taken whole by one at a time")

lazy val root = (project in file("."))
  .aggregate(a, b, c, d, e)
  .settings(
    check := {
      val log = streams.value.log
      val recs = IO.listFiles((ThisBuild / recordsDir).value).toSeq.flatMap { f =>
        IO.read(f).linesIterator.filter(_.trim.nonEmpty).map { line =>
          val Array(proj, pid, s, e) = line.trim.split(" ")
          (proj, pid, s.toLong, e.toLong)
        }
      }
      if (recs.isEmpty) sys.error("no classes recorded a window")
      val projects = recs.map(_._1).distinct.sorted
      if (projects.size != 5) sys.error(s"expected all five projects to run, saw ${projects.mkString(",")}")

      // Did two DIFFERENT projects ever hold a JVM at the same moment?
      val crossOverlap = recs.combinations(2).exists {
        case Seq((p1, _, s1, e1), (p2, _, s2, e2)) => p1 != p2 && math.min(e1, e2) - math.max(s1, s2) > 0
        case _                                     => false
      }
      // The widest a single project spread: how many of its own JVMs were live at once.
      val widest = recs.groupBy(_._1).map { case (p, rs) =>
        p -> rs.map { case (_, _, s, _) => rs.count { case (_, _, s2, e2) => s2 <= s && s < e2 } }.max
      }
      if (!crossOverlap)
        sys.error(
          "no two projects ever held a JVM at the same time; the pool went to one project at a time " +
            s"(widest spread per project: ${widest.toSeq.sorted.mkString(", ")})"
        )
      // Spreading is not banned -- it is what a group should do once nothing else wants the slots, so a
      // project that is the only one left with work may hold both. What must not happen is EVERY project
      // taking the whole pool in turn, which is the un-shared schedule this guards against.
      //
      // Deliberately no tighter than that. Priority only orders what a CompletionService is already
      // holding back: `submit` starts a task the moment its tags validate, so a project whose two workers
      // are submitted while the pool still has room takes both slots without priority being consulted at
      // all, and which project that is comes down to whichever reaches its test task first. A project
      // that is last to finish can spread for the legitimate reason too. Counting spreaders more strictly
      // than "not all of them" measures that scheduling luck rather than the sharing -- over five runs
      // here the single spreader was a, b, c or d depending on the run.
      val spreaders = widest.collect { case (p, n) if n > 1 => p }.toSeq.sorted
      if (spreaders.size == projects.size)
        sys.error(
          "every project held both JVMs at some point, so the pool went to one project at a time " +
            s"rather than being shared (widest per project: ${widest.toSeq.sorted.mkString(", ")})"
        )
      // widest is logged either way, so a schedule that drifts towards monopolising the pool is visible
      // in a passing run rather than only once it trips the assertion.
      log.info(
        s"pool shared across projects; ${if (spreaders.isEmpty) "no project" else spreaders.mkString(", ")}" +
          s" spread to both JVMs, widest per project ${widest.toSeq.sorted.mkString(", ")} (${recs.size} classes)"
      )
    }
  )
