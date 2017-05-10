import sbt.internal.inc.Analysis
import complete.DefaultParsers._

// Reset compiler iterations, necessary because tests run in batch mode
val recordPreviousIterations = taskKey[Unit]("Record previous iterations.")
recordPreviousIterations := {
  CompileState.previousIterations = {
    val previousAnalysis = (previousCompile in Compile).value.analysis
    if (previousAnalysis.isEmpty) {
      streams.value.log.info("No previous analysis detected")
      0
    } else {
      previousAnalysis.get match {
        case a: Analysis => a.compilations.allCompilations.size
      }
    }
  }
}

val checkIterations = inputKey[Unit]("Verifies the accumulated number of iterations of incremental compilation.")

checkIterations := {
  val expected: Int = (Space ~> NatBasic).parsed
  val actual: Int = ((compile in Compile).value match { case a: Analysis => a.compilations.allCompilations.size }) - CompileState.previousIterations
  assert(expected == actual, s"Expected $expected compilations, got $actual")
}
