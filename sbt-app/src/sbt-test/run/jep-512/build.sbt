@transient
lazy val check = taskKey[Unit]("")

lazy val common = Def.settings(
  // 2.12.x uses Zinc's compiler bridge
  scalaVersion := "2.12.21",
)

// use `runMain` instead of `run` because discoveredMainClasses return empty
// if JEP-512 java main method
// TODO fix zinc
// https://github.com/sbt/sbt/issues/7384#issuecomment-3361020003
lazy val commonRunMainCheck = Def.settings(
  check := {
    if (scala.util.Properties.isJavaAtLeast("25")) {
      (Compile / runMain).toTask(" example.A").value
    } else ()
  }
)

lazy val a1 = project
  .settings(common)
  .settings(
    check := {
      if (scala.util.Properties.isJavaAtLeast("25")) {
        assert((Compile / discoveredMainClasses).value.size == 1)
        (Compile / run).toTask(" ").value
      } else ()
    }
  )

lazy val a2 = project.settings(common, commonRunMainCheck)
lazy val a3 = project.settings(common, commonRunMainCheck)
lazy val a4 = project.settings(common, commonRunMainCheck)

lazy val a5 = project.settings(
  common,
  check := {
    if (scala.util.Properties.isJavaAtLeast("25")) {
      (Compile / runMain).toTask(" example.A").value
    } else {
      sys.error("not jdk 25")
    }
  }
)

lazy val a6 = project.settings(
  common,
  check := {
    if (scala.util.Properties.isJavaAtLeast("25")) {
      (Compile / runMain).toTask(" A").value
    } else ()
  }
)
