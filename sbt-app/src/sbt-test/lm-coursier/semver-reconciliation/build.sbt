import lmcoursier.definitions.*
import lmcoursier.syntax.*

lazy val semver61 = project
  .settings(
    scalaVersion := "2.11.12",
    libraryDependencies ++= Seq(
      "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M11",
      "io.argonaut" %% "argonaut" % "6.1"
    ),
    csrReconciliations += ModuleMatchers.all -> Reconciliation.SemVer
  )

lazy val semver62 = project
  .settings(
    scalaVersion := "2.11.12",
    libraryDependencies ++= Seq(
      "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M11",
      "io.argonaut" %% "argonaut" % "6.2"
    ),
    csrReconciliations += ModuleMatchers.all -> Reconciliation.SemVer
  )

lazy val strict62 = project
  .settings(
    scalaVersion := "2.11.12",
    libraryDependencies ++= Seq(
      "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M11",
      "io.argonaut" %% "argonaut" % "6.2"
    ),
    csrReconciliations += ModuleMatchers.all -> Reconciliation.Strict
  )

