ThisBuild / organization := "com.example"

lazy val root = (project in file("."))

lazy val marker = (project in file("marker"))
  .settings(
    name := "marker",
    version := "0.1.0-SNAPSHOT",
    publishMavenStyle := false,
    publishTo := Some(
      Resolver.file("test-repo", (ThisBuild / baseDirectory).value / "global" / "repo")(using
        Resolver.ivyStylePatterns
      )
    ),
  )

val startRepoServer = taskKey[Unit]("Serves global/repo over HTTP on a free port")
val stopRepoServer = taskKey[Unit]("Stops the repository server")

Global / startRepoServer := Def.uncached {
  val global = (ThisBuild / baseDirectory).value / "global"
  RepoServer.start(global / "repo", global / "repo-port")
}
Global / stopRepoServer := Def.uncached(RepoServer.stop())
