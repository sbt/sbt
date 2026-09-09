import Tests._
import Defaults._

// 4 classes, 4 workers: TestTopology.subprojectSplit(classCount) puts one class per group,
// so testOnly selecting a single class leaves the other 3 groups empty after Tests.processOptions filters them down.
val classCount = 4

val checkOne = TaskKey[Unit]("checkOne", "Check setup/cleanup ran exactly once, then reset.")
val checkAll = TaskKey[Unit](
  "checkAll",
  "Check setup/cleanup ran once per class (no filtering), then reset."
)

scalaVersion := "3.8.4"
organization := "com.example"

Test / fork := true
Global / workerMaxInstances := classCount
Test / testTopology := TestTopology.subprojectSplit(classCount)

libraryDependencies += "org.scalameta" %% "munit" % "1.0.4" % Test

Test / testOptions += {
  val baseDir = baseDirectory.value
  Tests.Setup { () =>
    IO.append(baseDir / "setup-log", "x\n")
  }
}
Test / testOptions += {
  val baseDir = baseDirectory.value
  Tests.Cleanup { () =>
    IO.append(baseDir / "cleanup-log", "x\n")
  }
}

def checkTask(expected: Int) = Def.uncached {
  def count(name: String): Int =
    if file(name).exists then IO.readLines(file(name)).count(_.nonEmpty) else 0
  val setups = count("setup-log")
  val cleanups = count("cleanup-log")
  if setups != expected || cleanups != expected then
    sys.error(
      s"Expected setup/cleanup to run $expected times, saw setup=$setups cleanup=$cleanups"
    )
  IO.delete(file("setup-log"))
  IO.delete(file("cleanup-log"))
}

checkOne := checkTask(1)
checkAll := checkTask(classCount)
