
lazy val b = project
  .settings(
    apiURL := Some(uri(s"http://example.org/b")),
    publishMavenStyle := false,
    publishTo := Some(bResolver.value),
    organization := "b",
    version := "0.1.0-SNAPSHOT"
  )

lazy val bResolver = Def.setting {
  val dir = (ThisBuild / baseDirectory).value / "b-repo"
  Resolver.file("b-resolver", dir)(using Resolver.defaultIvyPatterns)
}

lazy val check = taskKey[Unit]("")

check := {
  import java.nio.file._
  val f = (ThisBuild / baseDirectory).value / "b-repo/b/b_3/0.1.0-SNAPSHOT/ivys/ivy.xml"
  assert(f.exists(), s"missing $f")
  val content = Files.readString(f.toPath)
  assert(content.contains("""e:info.apiURL="http://example.org/b""""))
}
