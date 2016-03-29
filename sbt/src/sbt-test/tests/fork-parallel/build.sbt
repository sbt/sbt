import Tests._
import Defaults._

val check = taskKey[Unit]("Check that tests are executed in parallel")

lazy val root = (project in file(".")).
  settings(
    scalaVersion := "2.9.2",
    libraryDependencies += "com.novocode" % "junit-interface" % "0.10" % "test",
    fork in Test := true,
    check := {
      val nbProc = java.lang.Runtime.getRuntime().availableProcessors()
      if( nbProc < 4 ) {
        streams.value.log.warn("With fewer than 4 processors this test is meaningless")
      } else {
        // we've got at least 4 processors, we'll check the upper end but also 3 and 4 as the upper might not
        // be reached if the system is under heavy load.
        if( ! (file("max-concurrent-tests_3").exists() || file("max-concurrent-tests_4").exists() ||
               file("max-concurrent-tests_" + (nbProc -1)).exists() || file("max-concurrent-tests_" + nbProc).exists())) {
          sys.error("Forked tests were not executed in parallel!")
        }
      }
    }
  )
