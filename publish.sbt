publishTo :=  {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.trim.endsWith("SNAPSHOT")) {
    Some("snapshots" at nexus + "content/repositories/snapshots")
  }
  else {
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

scmInfo := Some(
  ScmInfo(
    browseUrl = url("https://github.com/jrudolph/sbt-dependency-graph"),
    connection = "scm:git:git@github.com:jrudolph/sbt-dependency-graph.git"
  )
)

developers := List(
  Developer(
    "jrudolph",
    "Johannes Rudolph",
    "johannes.rudolph@gmail.com",
    url("http://virtual-void.net")
  )
)

useGpg := true
