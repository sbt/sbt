// Four projects sharing one pool of four forked test JVMs.
//
// b, c and d have a single test class each, so each takes one JVM and cannot spread. a has twenty.
// The point is what happens when b, c and d finish: their JVMs exit, sbt admits more of a's worker
// tasks, and each forks a *new* JVM that steals from a's queue, so a ends the run holding the whole
// pool. a's classes all block until that has happened — see Rec.awaitPool.

ThisBuild / scalaVersion := "2.12.21"

Global / testForkedWorkStealing := true

// Four slots: one each for b, c and d at the start, and all four for a once they are done. This one
// rule does both jobs, capping the concurrent forked test JVMs and sizing how far a group may
// spread. Replacing the Seq rather than appending, since += only tightens.
Global / concurrentRestrictions := Seq(
  Tags.limitAll(java.lang.Runtime.getRuntime.availableProcessors),
  Tags.limit(Tags.ForkedTestGroup, 4),
  Tags.exclusiveGroup(Tags.Clean)
)

val pidsDir = settingKey[File]("Where forked test JVMs record which classes they ran")
ThisBuild / pidsDir := (ThisBuild / baseDirectory).value / "pids"

// Capped by the processor count because Tags.limitAll is, so the expectation holds on a two-core CI
// box as well as a large one, where it degrades to a trivially true assertion.
val expectedJvms = settingKey[Int]("How many JVMs project a should end up holding")
ThisBuild / expectedJvms := math.min(4, java.lang.Runtime.getRuntime.availableProcessors)

lazy val commonSettings = Seq(
  libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test,
  Test / fork := true,
  Test / javaOptions ++= Seq(
    s"-Dpids.dir=${(ThisBuild / pidsDir).value}",
    s"-Dproj=${name.value}",
    s"-Dexpect.jvms=${(ThisBuild / expectedJvms).value}"
  )
)

lazy val a = project.settings(commonSettings)
lazy val b = project.settings(commonSettings)
lazy val c = project.settings(commonSettings)
lazy val d = project.settings(commonSettings)

val check = taskKey[Unit]("A holds the whole pool once b, c and d have released it")

lazy val root = (project in file("."))
  .aggregate(a, b, c, d)
  .settings(
    check := {
      val want = (ThisBuild / expectedJvms).value
      val log = streams.value.log
      // Each marker is `<project>.<suite>.<pid>`, so these are plain facts about what ran where.
      val runs = IO.listFiles((ThisBuild / pidsDir).value).toSeq.map { f =>
        val parts = f.getName.split('.')
        (parts(0), parts(1), parts(2))
      }
      val byProject = runs.groupBy(_._1)

      Seq("a", "b", "c", "d").foreach { p =>
        if (!byProject.contains(p)) sys.error(s"project $p never ran any tests")
      }

      // b, c and d have one class each, so a project that cannot spread must not have spread.
      Seq("b", "c", "d").foreach { p =>
        val pids = byProject(p).map(_._3).distinct
        if (pids.size != 1)
          sys.error(s"$p has a single test class and must use one JVM, saw ${pids.size}: $pids")
      }

      val aPids = byProject("a").map(_._3).distinct
      if (aPids.size != want)
        sys.error(s"expected project a to end up with $want JVMs, saw ${aPids.size}: $aPids")

      val aSuites = byProject("a").map(_._2).distinct
      if (aSuites.size != 20)
        sys.error(s"expected all 20 of a's classes to run, saw ${aSuites.size}")

      // The proof of late forking: at most four JVMs may run at once, so more than four distinct
      // JVMs over the run means the surplus was forked into slots earlier JVMs had released.
      val total = runs.map(_._3).distinct.size
      if (want > 1 && total <= 4)
        sys.error(s"expected more than 4 distinct JVMs over the run, saw $total: no slot was reused")
      log.info(s"a used ${aPids.size} JVMs, $total forked in total across the four projects")
    }
  )
