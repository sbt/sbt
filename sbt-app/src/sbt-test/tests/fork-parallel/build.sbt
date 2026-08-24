import Tests._
import Defaults._

scalaVersion := "2.12.21"

@transient
val check = taskKey[Unit]("Check that tests are executed in parallel at the default of 2 threads")

@transient
val checkAtLeast4 = taskKey[Unit]("Check that testForkedParallelism raises concurrency above the default")

libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test
Test / fork := true
// Pin to one JVM at the subproject-level
testForkedWorker := 1

check := {
  // testForkedParallelism unset: the default is a flat 2 threads per worker JVM, not
  // availableProcessors -- JVM count (testForkedWorker) is the parallelism dial now, not in-JVM
  // threads. So we expect concurrency of exactly 2, never more.
  if !file("max-concurrent-tests_2").exists() then
    sys.error("Forked tests were not executed in parallel at the default of 2!")
  if file("max-concurrent-tests_3").exists() || file("max-concurrent-tests_4").exists() then
    sys.error("Forked tests exceeded the default parallelism of 2 -- did the default change?")
}

checkAtLeast4 := {
  val nbProc = java.lang.Runtime.getRuntime().availableProcessors()
  val log = streams.value.log
  if nbProc < 4 then
    log.warn("With fewer than 4 processors this check is meaningless")
  else if !(file("max-concurrent-tests_3").exists() || file("max-concurrent-tests_4").exists()) then
    sys.error("testForkedParallelism := Some(4) did not raise concurrency above the default of 2!")
}
