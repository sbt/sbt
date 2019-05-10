Compile / compile := {
  Count.increment()
  // Trigger a new build by updating the last modified time
  val extra = baseDirectory.value / "extra.sbt"
  IO.copyFile(baseDirectory.value / "changes" / "extra.sbt", extra, CopyOptions().withOverwrite(true))
  (Compile / compile).value
}
