import sbt.internal.util.EscHelpers

scalaVersion := "2.13.16"

def junit = libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test

Global / localCacheDirectory := baseDirectory.value / "diskcache"
Global / testResultLogger := sbt.recaplog.Capture.captureTestResultLogger
commands ++= Seq(sbt.recaplog.Capture.captureLog, sbt.recaplog.Capture.captureTestLog)

@transient
lazy val dropSuccess = inputKey[Unit]("")

@transient
lazy val dropSuccessSorted = inputKey[Unit]("")

lazy val a = project.settings(junit)
lazy val b = project.settings(junit)
lazy val c = project.settings(junit)

lazy val root = rootProject
  .aggregate(a, b, c)
  .settings(
    dropSuccess / aggregate := false,
    dropSuccess := {
      val fileName = Def.spaceDelimited("<log file>").parsed.head
      val log = baseDirectory.value / "target" / fileName
      val dropped = baseDirectory.value / "target" / "drop.log"
      IO.writeLines(dropped, IO.readLines(log)
        .map(EscHelpers.stripColorsAndMoves)
        .filterNot { line =>
          line.startsWith("[success]") ||
          line.contains("elapsed time:") ||
          line.contains("[info] set current project") ||
          line.contains("Defining Global / testSummary") ||
          line.contains("The new value will be used by no settings or tasks.") ||
          line.contains("Reapplying settings...")
        })
    },
    // sbt runs independent subprojects' tasks in parallel, so testQuick's
    // per-project lines don't land in a fixed order; sort them so the
    // golden comparison isn't flaky. (The other captures above are the
    // deterministic, already-ordered TestSummary rendering, so they don't
    // need this.)
    dropSuccessSorted / aggregate := false,
    dropSuccessSorted := {
      val fileName = Def.spaceDelimited("<log file>").parsed.head
      val log = baseDirectory.value / "target" / fileName
      val dropped = baseDirectory.value / "target" / "drop.log"
      IO.writeLines(dropped, IO.readLines(log)
        .map(EscHelpers.stripColorsAndMoves)
        .filterNot { line =>
          line.startsWith("[success]") || line.contains("elapsed time:")
        }
        .sorted)
    }
  )
