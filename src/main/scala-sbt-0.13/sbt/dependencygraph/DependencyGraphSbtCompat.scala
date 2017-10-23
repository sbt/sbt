package sbt.dependencygraph

object DependencyGraphSbtCompat {
  object Implicits {
    implicit def convertConfig(config: sbt.Configuration): String = config.toString
  }
}
