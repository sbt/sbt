import sbt.Keys.version
import sbt._
object DependentPlugin extends AutoPlugin {
  override def requires = sbt.test.TestPlugin

  override def trigger = AllRequirements


}