val test123 = project in file(".") enablePlugins TestP settings(
  resourceGenerators in Compile += Def.task {
    streams.value.log info "resource generated in settings"
    Nil
  }
)

TaskKey[Unit]("check") := {
  val last = IO read (BuiltinCommands lastLogFile state.value).get
  def assertContains(expectedString: String) =
    if (!(last contains expectedString)) sys error s"Expected string $expectedString to be present"
  assertContains("resource generated in settings")
  assertContains("resource generated in plugin")
}
