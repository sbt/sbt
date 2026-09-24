import sbt.internal.LoadedBuild

lazy val root = project.in(file("."))

def detectedPlugins(lb: LoadedBuild): Seq[String] =
  lb.units(lb.root).unit.plugins.detected.autoPlugins.map(_.name)

InputKey[Unit]("checkPlugins") := {
  val args = Def.spaceDelimited("<names>").parsed
  val detected = detectedPlugins(loadedBuild.value)
  args.foreach { name =>
    assert(
      detected.exists(_.contains(name)),
      s"expected plugin $name to be detected, got: ${detected.mkString(", ")}"
    )
  }
}

InputKey[Unit]("checkPluginsAbsent") := {
  val args = Def.spaceDelimited("<names>").parsed
  val detected = detectedPlugins(loadedBuild.value)
  args.foreach { name =>
    assert(
      !detected.exists(_.contains(name)),
      s"expected plugin $name not to be detected, got: ${detected.mkString(", ")}"
    )
  }
}

// Compares the whole list of registered paths, in order. This also catches a path that comes
// back from a reboot changed, doubled or in a different place, not only one that is lost.
// With no arguments it asserts that no file is registered.
InputKey[Unit]("checkFiles") := {
  val expected = Def.spaceDelimited("<paths>").parsed.toList
  val actual = state.value.get(BasicKeys.extraMetaSbtFiles).toList.flatten.map(_.id)
  assert(
    actual == expected,
    s"expected registered files to be [${expected.mkString(", ")}], got: [${actual.mkString(", ")}]"
  )
}

// Tests in a scripted group share one sbt session, and the extra files now survive reboot on
// purpose, so they have to be dropped before the next test.
commands += Command.command("clearExtraPluginSbtFiles") { s =>
  s.remove(BasicKeys.extraMetaSbtFiles)
}
