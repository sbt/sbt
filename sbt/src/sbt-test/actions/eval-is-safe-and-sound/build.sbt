lazy val common = project

lazy val boink = project

lazy val woof = project


lazy val numConfigClasses = taskKey[Int]("counts number of config classes")

lazy val configClassCountFile = settingKey[File]("File where we write the # of config classes")

lazy val saveNumConfigClasses = taskKey[Unit]("Saves the number of config classes")

lazy val checkNumConfigClasses = taskKey[Unit]("Checks the number of config classes")

lazy val checkDifferentConfigClasses = taskKey[Unit]("Checks that the number of config classes are different.")

configClassCountFile := (target.value / "config-count")

numConfigClasses := {
  val cdir = (baseDirectory in ThisBuild).value / "project/target/config-classes"
  (cdir.allPaths --- cdir).get.length
}

saveNumConfigClasses := {
  IO.write(configClassCountFile.value, numConfigClasses.value.toString)
}

def previousConfigCount = Def.task {
  val previousString = IO.read(configClassCountFile.value)
  try Integer.parseInt(previousString)
  catch {
    case t: Throwable => throw new RuntimeException(s"Failed to parse previous config file value: $previousString", t)
  }
}

checkDifferentConfigClasses := {
  val previousString = IO.read(configClassCountFile.value)
  val previous = previousConfigCount.value
  val current = numConfigClasses.value
  assert(previous != current, s"Failed to create new configuration classes.  Expected: $previous, Found: $current")
}

checkNumConfigClasses := {
  val previousString = IO.read(configClassCountFile.value)
  val previous = previousConfigCount.value
  val current = numConfigClasses.value
  assert(previous == current, s"Failed to delete extra configuration classes.  Expected: $previous, Found: $current")
}
