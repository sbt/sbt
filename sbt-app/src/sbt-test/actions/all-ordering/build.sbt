lazy val marker = taskKey[String]("Identifies the selected scope.")

lazy val checkProjectOrdering = taskKey[Unit]("Checks deterministic project ordering.")
lazy val checkConfigOrdering = taskKey[Unit]("Checks deterministic configuration ordering.")
lazy val checkAllOrdering = taskKey[Unit]("Runs all ScopeFilter ordering checks.")

def assertEquals[A](actual: Seq[A], expected: Seq[A], context: String): Unit =
  assert(actual == expected, s"$context expected=$expected actual=$actual")

lazy val a = project.settings(
  marker := "a-global",
  Compile / marker := "a-compile",
  Test / marker := "a-test",
)

lazy val b = project.settings(
  marker := "b-global",
  Compile / marker := "b-compile",
  Test / marker := "b-test",
)

lazy val c = project.settings(
  marker := "c-global",
  Compile / marker := "c-compile",
  Test / marker := "c-test",
)

lazy val root = project
  .aggregate(a, b, c)
  .settings(
    checkProjectOrdering := {
      val values = marker.all(ScopeFilter(inProjects(c, a, b))).value
      assertEquals(values, Seq("c-global", "a-global", "b-global"), "project ordering")
    },
    checkConfigOrdering := {
      val values = marker.all(
        ScopeFilter(
          projects = inProjects(b),
          configurations = inConfigurations(Test, Compile) || inZeroConfiguration
        )
      ).value
      assertEquals(values, Seq("b-compile", "b-test", "b-global"), "configuration ordering")
    },
    checkAllOrdering := {
      checkProjectOrdering.value
      checkConfigOrdering.value
    },
  )
