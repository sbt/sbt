
lazy val b = project
  .settings(
    apiURL := Some(url(s"http://example.org/b")),
    publishMavenStyle := false,
    publishTo := Some(bResolver.value),
    organization := "b",
    version := "0.1.0-SNAPSHOT"
  )

lazy val bResolver = Def.setting {
  val dir = baseDirectory.in(ThisBuild).value / "b-repo"
  Resolver.file("b-resolver", dir)(Resolver.defaultIvyPatterns)
}

lazy val check = taskKey[Unit]("")

check := {
  import java.nio.file._
  val f = baseDirectory.in(ThisBuild).value / "b-repo/b/b_2.12/0.1.0-SNAPSHOT/ivys/ivy.xml"
  assert(f.exists())
  val content = new String(Files.readAllBytes(f.toPath), "UTF-8")
  assert(content.contains("""e:info.apiURL="http://example.org/b""""))
}
