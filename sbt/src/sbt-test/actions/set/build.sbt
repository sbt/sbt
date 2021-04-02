
TaskKey[Unit]("checkName", "") := {
	assert(name.value == "hello-world", "Name is not hello-world, failed to set!")
}

val notExistingThing = settingKey[Int]("Something new")

TaskKey[Unit]("checkBuildSbtDefined", "") := {
	assert(notExistingThing.?.value == Some(5), "Failed to set a settingKey defined in build.sbt")
}

TaskKey[Unit]("evil-clear-logger") := {
  val logger = sLog.value
  val cls = logger.getClass
  val field = cls.getDeclaredField("ref")
  field.setAccessible(true)
  val ref = field.get(logger).asInstanceOf[java.lang.ref.WeakReference[_]]
  // MUHAHAHHAHAHAHAHHAHA, I am de evil GC, I clear things.
  ref.clear()
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