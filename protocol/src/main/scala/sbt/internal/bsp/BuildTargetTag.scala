package sbt.internal.bsp

// https://build-server-protocol.github.io/docs/specification.html#build-target
object BuildTargetTag {
  val test: String = "test"
  val application: String = "application"
  val library: String = "library"
  val integrationTest: String = "integration-test"
  val benchmark: String = "benchmark"
  val noIDE: String = "no-ide"

  def fromConfig(config: String): Vector[String] = config match {
    case "test"    => Vector(test)
    case "compile" => Vector(library)
    case _         => Vector.empty
  }
}
