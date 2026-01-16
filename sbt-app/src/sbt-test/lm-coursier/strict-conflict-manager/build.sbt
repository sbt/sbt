
import lmcoursier.definitions.*
import lmcoursier.syntax.*

lazy val shared = Seq(
  scalaVersion := "2.12.8",
  libraryDependencies ++= Seq(
    "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M4",
    "com.chuusai" %% "shapeless" % "2.3.3"
  ),
  conflictManager := ConflictManager.strict
)

lazy val a = project
  .settings(shared)

lazy val b = project
  .settings(shared)
  .settings(
    // strict cm should be fine if we force the conflicting module version
    dependencyOverrides += "com.chuusai" %% "shapeless" % "2.3.3"
  )

lazy val c = project
  .settings(
    // no shared settings here
    scalaVersion := "2.12.11",
    libraryDependencies ++= Seq(
      "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0",
      "com.chuusai" %% "shapeless" % "2.3.2"
    ),
    csrReconciliations += {
      val sv = scalaBinaryVersion.value
      ModuleMatchers.only("com.github.alexarchambault", s"argonaut-shapeless_6.2_$sv") -> Reconciliation.Strict
    }
  )

