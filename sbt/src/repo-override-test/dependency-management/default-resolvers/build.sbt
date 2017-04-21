lazy val check = taskKey[Unit]("")

lazy val root = (project in file(".")).settings(
  autoScalaLibrary := false,
  check := {
    val ar = appResolvers.value.get
    assert(!(ar exists { _.name == "jcenter" }))
    assert(!(ar exists { _.name == "public" }))
  }
)
