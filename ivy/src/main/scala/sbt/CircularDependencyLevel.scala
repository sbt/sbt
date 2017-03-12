package sbt

import org.apache.ivy.plugins.circular.{
  CircularDependencyStrategy,
  ErrorCircularDependencyStrategy,
  IgnoreCircularDependencyStrategy,
  WarnCircularDependencyStrategy
}

/**
  * Wrapper around circular dependency strategy.
  */
sealed trait CircularDependencyLevel {
  private[sbt] def ivyStrategy: CircularDependencyStrategy
  private[sbt] def name: String
  override def toString: String = name
}

object CircularDependencyLevel {
  val Warn: CircularDependencyLevel = new CircularDependencyLevel {
    def ivyStrategy: CircularDependencyStrategy = WarnCircularDependencyStrategy.getInstance
    def name: String = "warn"
  }
  val Ignore: CircularDependencyLevel = new CircularDependencyLevel {
    def ivyStrategy: CircularDependencyStrategy = IgnoreCircularDependencyStrategy.getInstance
    def name: String = "ignore"
  }
  val Error: CircularDependencyLevel = new CircularDependencyLevel {
    def ivyStrategy: CircularDependencyStrategy = ErrorCircularDependencyStrategy.getInstance
    def name: String = "error"
  }
}
