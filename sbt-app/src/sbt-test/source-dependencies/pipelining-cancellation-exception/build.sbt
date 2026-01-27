ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.4"

ThisBuild / usePipelining := true

lazy val A = (project in file("A"))
  .settings(
    name := "A"
  )

lazy val B = (project in file("B"))
  .settings(
    name := "B"
  ).dependsOn(A)

// Custom task to verify that compilation fails gracefully without CancellationException
lazy val root = (project in file("."))
  .aggregate(A, B)
  .settings(
    TaskKey[Unit]("verifyNoCancellationException") := {
      val s = streams.value
      try {
        (B / Compile / compile).value
        sys.error("Expected compilation to fail")
      } catch {
        case e: sbt.Incomplete =>
          // Check that the cause is not a CancellationException
          val allCauses = sbt.Incomplete.allExceptions(e)
          val hasCancellationException = allCauses.exists {
            case ce: java.util.concurrent.CancellationException => true
            case _ => false
          }
          if (hasCancellationException) {
            sys.error("CancellationException was thrown and not handled properly")
          }
          // Compilation failed as expected, and no CancellationException was thrown
          s.log.info("Compilation failed gracefully without CancellationException")
        case e: Throwable =>
          // Check if it's a CancellationException
          if (e.isInstanceOf[java.util.concurrent.CancellationException]) {
            sys.error("CancellationException was thrown and not handled properly")
          }
          throw e
      }
    }
  )

