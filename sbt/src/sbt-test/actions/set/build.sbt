
TaskKey[Unit]("checkName", "") := {
	assert(name.value == "hello-world", "Name is not hello-worled, failed to set!")
}

val notExistingThing = settingKey[Int]("Something new")

TaskKey[Unit]("checkBuildSbtDefined", "") := {
	assert(notExistingThing.?.value == Some(5), "Failed to set a settingKey defined in build.sbt")
}

commands ++= Seq(
  Command.command("helloWorldTest") { state: State =>
     """set name := "hello-world"""" ::
     "checkName" :: 
     state
  },
  Command.command("buildSbtTest") { state: State =>
     """set notExistingThing := 5""" ::
     "checkBuildSbtDefined" :: 
     state
  }
)