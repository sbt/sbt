lazy val check = taskKey[Unit]("")
lazy val check2 = taskKey[Unit]("")

lazy val root = (project in file(".")).
  settings(
    check := {
      val fr = fullResolvers.value
      assert(!(fr exists { _.name == "jcenter" }))
      assert(fr exists { _.name == "public" })
    },
    check2 := {
      val fr = fullResolvers.value
      assert(fr exists { _.name == "jcenter" })
      assert(fr exists { _.name == "public" })
    }
  )
