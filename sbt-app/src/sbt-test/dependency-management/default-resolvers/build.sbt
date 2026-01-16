lazy val check = taskKey[Unit]("")

lazy val root = (project in file(".")).
  settings(
    check := Def.uncached {
      val fr = fullResolvers.value
      assert(!(fr exists { _.name == "jcenter" }))
      assert(fr exists { _.name == "public" })
      ()
    },
  )
