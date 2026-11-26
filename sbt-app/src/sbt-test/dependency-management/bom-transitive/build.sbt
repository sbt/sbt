ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

// A BOM declared with .pomOnly() manages transitive dependencies:
// json4s-jackson 4.0.7 depends on jackson-databind 2.12.7.
scalaVersion := "2.12.18"

libraryDependencies ++= Seq(
  ("com.fasterxml.jackson" % "jackson-bom" % "2.18.2").pomOnly(),
  "org.json4s" %% "json4s-jackson" % "4.0.7"
)

@transient
lazy val check = inputKey[Unit]("Check the resolved jackson-databind version")

check := locally:
  val expected = Def.spaceDelimited("<version>").parsed.head
  val resolved = (Compile / externalDependencyClasspath).value
    .flatMap(_.get(Keys.moduleIDStr))
    .map(Classpaths.moduleIdJsonKeyFormat.read)
    .filter(m => m.organization == "com.fasterxml.jackson.core" && m.name == "jackson-databind")
    .map(_.revision)
  assert(resolved == Seq(expected), s"Expected jackson-databind $expected, got $resolved")
