import sbt._

object Q extends RootAutoPlugin {
  override def projectSettings: Seq[Setting[_]] = Seq(
    TaskKey[Unit]("foo") := {
      println("Since we got here, the plugin must be installed")
    }
  )
}
