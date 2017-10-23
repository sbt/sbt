package sbt.dependencygraph

object DependencyGraphSbtCompat {
  object Implicits {
    implicit def convertConfig(config: sbt.Configuration): sbt.Configuration = config
  }
}
