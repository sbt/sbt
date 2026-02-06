lazy val check = taskKey[Unit]("Runs the check")

// Reproduces issue #8665: platform should not be applied to auto-injected Scala library
scalaVersion := "3.7.4"
platform := "native0.5"

// autoScalaLibrary is true by default, which auto-injects scala3-library
// The auto-injected library has .platform(Platform.jvm) explicitly set
// It should NOT get the native0.5 platform suffix

TaskKey[Unit]("check") := {
  val ur = update.value
  
  // The auto-injected scala3-library should be resolved as scala3-library_3, NOT scala3-library_native0.5_3
  val scala3LibraryFiles = ur.matching(
    moduleFilter(organization = "org.scala-lang", name = "scala3-library_3", revision = "*")
  )
  assert(
    scala3LibraryFiles.nonEmpty,
    s"scala3-library_3 (without platform suffix) was not found in update report. " +
    s"This indicates the platform was incorrectly applied to the auto-injected library. " +
    s"Update report: $ur"
  )
  
  // Verify that scala3-library_native0.5_3 does NOT exist (it shouldn't)
  val wrongFiles = ur.matching(
    moduleFilter(organization = "org.scala-lang", name = "scala3-library_native0.5_3", revision = "*")
  )
  assert(
    wrongFiles.isEmpty,
    s"scala3-library_native0.5_3 was incorrectly found in update report. " +
    s"The auto-injected Scala library should not get the project platform suffix. " +
    s"Update report: $ur"
  )
  
  streams.value.log.info("✓ Auto-injected scala3-library correctly resolved without platform suffix")
}

csrCacheDirectory := baseDirectory.value / "coursier-cache"

