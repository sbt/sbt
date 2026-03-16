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
    TaskKey[Unit]("saveIndexTimestamp") := {
      val apiDir = (Compile / doc / target).value
      val index = apiDir / "index.html"
      assert(index.exists(), s"Expected $index to exist")
      val marker = baseDirectory.value / "target" / "saved-index-ts"
      sbt.io.IO.write(marker, index.lastModified().toString)
    },
    TaskKey[Unit]("checkIndexNotRegenerated") := {
      val apiDir = (Compile / doc / target).value
      val index = apiDir / "index.html"
      val marker = baseDirectory.value / "target" / "saved-index-ts"
      val savedTs = sbt.io.IO.read(marker).trim.toLong
      val currentTs = index.lastModified()
      assert(savedTs == currentTs, s"Expected doc to be cached (index.html unchanged) but timestamps differ: saved=$savedTs current=$currentTs")
    },
  )
