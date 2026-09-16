Global / localCacheDirectory := baseDirectory.value / "diskcache"

ThisBuild / evictionErrorLevel := Level.Info
libraryDependencies += "org.scala-sbt" % "sbt" % sbtVersion.value

lazy val expectErrorNotCrash = taskKey[Unit](
  "Ensures that sbt properly set types on Trees so that the compiler doesn't crash on a bad reference to .value, but gives a proper error instead."
)

expectErrorNotCrash := Def.uncached {
  val fail = (Compile / compileIncremental).failure.value
  Incomplete.allExceptions(fail).headOption match
    case Some(x: xsbti.CompileFailed) => ()
    case _ => sys.error("Compiler crashed instead of providing a compile-time-only exception.")
}

val expectedDiagnostic = settingKey[String]("")
@transient
val checkDiagnostic = taskKey[Unit]("")
expectedDiagnostic := "`taskValue` can only be used within a task or setting macro, such as :=, +=, ++=, Def.task, or Def.setting."
checkDiagnostic := {
  val expected = expectedDiagnostic.value
  val failure = (Compile / compileIncremental).failure.value
  val messages = Incomplete
    .allExceptions(failure)
    .collect { case failure: xsbti.CompileFailed =>
      failure.problems().toSeq.map(_.message())
    }
    .flatten
  assert(
    messages.exists(_ == expected),
    s"Expected: $expected; obtained: ${messages.mkString("; ")}"
  )
}

val neverRun = taskKey[String]("")
neverRun := sys.error("taskValue must not execute the referenced task")
val held = settingKey[Boolean]("")
inline def wrap(inline key: TaskKey[String]): Task[String] = key.taskValue
held := (wrap(neverRun) != null)

@transient
val checkTaskValues = taskKey[Unit]("")
checkTaskValues := {
  assert(held.value)
  assert(neverRun.taskValue != null)
  assert((Compile / neverRun).taskValue != null)
  assert(Def.task { sys.error("Initialize task must not run"): String }.taskValue != null)
  assert(Def.setting { neverRun.taskValue != null }.value)
}

val checkInputTaskValue = inputKey[Unit]("")
checkInputTaskValue := {
  assert(neverRun.taskValue != null)
}
