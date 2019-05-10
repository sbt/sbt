val checkReloaded = taskKey[Unit]("Asserts that the build was reloaded")
checkReloaded := { () }

watchOnIteration := { _ => sbt.nio.Watch.CancelWatch }

Compile / compile := {
  Count.increment()
  // Trigger a new build by updating the last modified time
  val file = (Compile / scalaSource).value / "A.scala"
  IO.write(file, IO.read(file) + ("\n" * Count.get))
  (Compile / compile).value
}
