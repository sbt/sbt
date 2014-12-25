
val repoFile = file("mvn-repo")

resolvers += "bad-mvn-repo" at repoFile.toURI.toURL.toString

resolvers += Resolver.typesafeIvyRepo("releases")

libraryDependencies += "bad" % "mvn" % "1.0"

TaskKey[Unit]("check") := {
  val cp = (fullClasspath in Compile).value
  def isTestJar(n: String): Boolean =
    (n contains "scalacheck") ||
    (n contains "specs2")
  val testLibs = cp map (_.data.getName) filter isTestJar
  assert(testLibs.isEmpty, s"Compile Classpath has test libs:\n * ${testLibs.mkString("\n * ")}")
}