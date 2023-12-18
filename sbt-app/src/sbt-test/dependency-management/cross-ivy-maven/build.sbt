
val repoFile = file("mvn-repo")

resolvers += "bad-mvn-repo" at repoFile.toURI.toURL.toString

resolvers += Resolver.typesafeIvyRepo("releases")

libraryDependencies += "bad" % "mvn" % "1.0"

TaskKey[Unit]("check") := {
  val cp = (Compile / fullClasspath).value
  def isTestJar(n: String): Boolean =
    (n contains "scalacheck") ||
    (n contains "specs2")
  val testLibs = cp map (_.data.name) filter isTestJar
  assert(testLibs.isEmpty, s"Compile Classpath has test libs:\n * ${testLibs.mkString("\n * ")}")
}