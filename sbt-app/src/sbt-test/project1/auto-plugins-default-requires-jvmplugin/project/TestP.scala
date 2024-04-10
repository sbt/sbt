import sbt._, Keys._

object TestP extends AutoPlugin {
  override def projectSettings: Seq[Setting[_]] = Seq(
    Compile / resourceGenerators += Def.task {
      streams.value.log.info("resource generated in plugin")
      Seq.empty[File]
    }
  )
}
