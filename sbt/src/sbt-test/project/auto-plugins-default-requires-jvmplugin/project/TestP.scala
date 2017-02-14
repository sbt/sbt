import sbt._, Keys._

object TestP extends AutoPlugin {
  override def projectSettings: Seq[Setting[_]] = Seq(
    resourceGenerators in Compile += Def.task {
      streams.value.log info "resource generated in plugin"
      Nil
    }
  )
}
