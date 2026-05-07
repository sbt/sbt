import sbt.internal.inc.Analysis
import complete.DefaultParsers.*

scalaVersion := "2.13.18"

val checkStampSize = inputKey[Unit]("Verifies the accumulated number of iterations of incremental compilation.")
checkStampSize := {
  val expected: Int = (Space ~> NatBasic).parsed
  val analysis = (Compile / compile).value match
    case a: Analysis => a
  println(s"analysis: $analysis")
  val sourceStampSize = analysis.readStamps.getAllSourceStamps.size
  assert(sourceStampSize == expected, s"sourceStampSize = $sourceStampSize")
}

