lazy val root = (project in file("."))
  .settings(
    name := "doc-output-deleted",
    TaskKey[Unit]("deleteApiDir") := {
      val apiDir = (Compile / doc / target).value
      sbt.io.IO.delete(apiDir)
    },
    TaskKey[Unit]("checkApiDirAbsent") := {
      val apiDir = (Compile / doc / target).value
      assert(!apiDir.exists(), s"Expected $apiDir to not exist")
    },
    TaskKey[Unit]("checkApiDirExists") := {
      val apiDir = (Compile / doc / target).value
      assert(apiDir.exists() && apiDir.list().length > 0, s"Expected $apiDir to exist and be non-empty")
    },
  )
