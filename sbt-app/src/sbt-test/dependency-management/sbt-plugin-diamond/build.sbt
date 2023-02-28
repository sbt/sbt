// sbt-plugin-example-diamond is a diamond graph of dependencies of sbt plugins.
//             sbt-plugin-example-diamond
//                        / \
// sbt-plugin-example-left   sbt-plugin-example-right
//                        \ /
//             sbt-plugin-example-bottom
// Depending on the version of sbt-plugin-example-diamond, we test different patterns
// of dependencies:
//  * Some dependencies were published using the deprecated Maven paths, some with the new
//  * Wheter the dependency on sbt-plugin-example-bottom needs conflict resolution or not

inThisBuild(
  Seq(
    csrCacheDirectory := baseDirectory.value / "coursier-cache"
  )
)

// only deprecated Maven paths
lazy val v1 = project
  .in(file("v1"))
  .settings(
    localCache,
    addSbtPlugin("ch.epfl.scala" % "sbt-plugin-example-diamond" % "0.1.0"),
    checkUpdate := checkUpdateDef(
      "sbt-plugin-example-diamond-0.1.0.jar",
      "sbt-plugin-example-left-0.1.0.jar",
      "sbt-plugin-example-right-0.1.0.jar",
      "sbt-plugin-example-bottom-0.1.0.jar",
    ).value
  )

// diamond and left use the new Maven paths
lazy val v2 = project
  .in(file("v2"))
  .settings(
    localCache,
    addSbtPlugin("ch.epfl.scala" % "sbt-plugin-example-diamond" % "0.2.0"),
    checkUpdate := checkUpdateDef(
      "sbt-plugin-example-diamond_2.12_1.0-0.2.0.jar",
      "sbt-plugin-example-left_2.12_1.0-0.2.0.jar",
      "sbt-plugin-example-right-0.1.0.jar",
      "sbt-plugin-example-bottom-0.1.0.jar",
    ).value
  )

// conflict resolution on bottom between new and deprecated Maven paths
lazy val v3 = project
  .in(file("v3"))
  .settings(
    localCache,
    addSbtPlugin("ch.epfl.scala" % "sbt-plugin-example-diamond" % "0.3.0"),
    checkUpdate := checkUpdateDef(
      "sbt-plugin-example-diamond_2.12_1.0-0.3.0.jar",
      "sbt-plugin-example-left_2.12_1.0-0.3.0.jar",
      "sbt-plugin-example-right-0.1.0.jar",
      "sbt-plugin-example-bottom_2.12_1.0-0.2.0.jar",
    ).value
  )

// right still uses the deprecated Maven path and it depends on bottom
// which uses the new Maven path
lazy val v4 = project
  .in(file("v4"))
  .settings(
    localCache,
    addSbtPlugin("ch.epfl.scala" % "sbt-plugin-example-diamond" % "0.4.0"),
    checkUpdate := checkUpdateDef(
      "sbt-plugin-example-diamond_2.12_1.0-0.4.0.jar",
      "sbt-plugin-example-left_2.12_1.0-0.3.0.jar",
      "sbt-plugin-example-right-0.2.0.jar",
      "sbt-plugin-example-bottom_2.12_1.0-0.2.0.jar",
    ).value
  )

// only new Maven paths with conflict resolution on bottom
lazy val v5 = project
  .in(file("v5"))
  .settings(
    localCache,
    addSbtPlugin("ch.epfl.scala" % "sbt-plugin-example-diamond" % "0.5.0"),
    checkUpdate := checkUpdateDef(
      "sbt-plugin-example-diamond_2.12_1.0-0.5.0.jar",
      "sbt-plugin-example-left_2.12_1.0-0.3.0.jar",
      "sbt-plugin-example-right_2.12_1.0-0.3.0.jar",
      "sbt-plugin-example-bottom_2.12_1.0-0.3.0.jar",
    ).value
  )

def localCache =
  ivyPaths := IvyPaths(baseDirectory.value, Some((ThisBuild / baseDirectory).value / "ivy-cache"))

lazy val checkUpdate = taskKey[Unit]("check the resolved artifacts")

def checkUpdateDef(expected: String*): Def.Initialize[Task[Unit]] = Def.task {
  val report = update.value
  val obtainedFiles = report.configurations
    .find(_.configuration.name == Compile.name)
    .toSeq
    .flatMap(_.modules)
    .flatMap(_.artifacts)
    .map(_._2)
  val obtainedSet = obtainedFiles.map(_.getName).toSet
  val expectedSet = expected.toSet + "scala-library.jar"
  assert(obtainedSet == expectedSet, obtainedFiles)
}
