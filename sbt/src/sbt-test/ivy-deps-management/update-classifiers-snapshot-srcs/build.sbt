def ivyHome = Def.setting((target in LocalRootProject).value / "ivy")
def localRepo = Def.setting((target in LocalRootProject).value / "local-repo")

val commonSettings = Seq[Def.Setting[_]](
  organization := "org.example",
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.11.7",
  ivyPaths := IvyPaths((baseDirectory in ThisBuild).value, Some(ivyHome.value)),
  fullResolvers := fullResolvers.value.filterNot(_ == projectResolver.value)
)

lazy val bippy = project settings (
  commonSettings,
  resolvers += Resolver.file("ivy-local", file(sys.props("user.home")) / ".ivy2" / "local")(Resolver.ivyStylePatterns),
  publishTo := Some(Resolver.file("local-repo", localRepo.value))
)

lazy val myapp = project settings (
  commonSettings,
  resolvers += new MavenRepository("local-repo", localRepo.value.toURL.toString) withLocalIfFile false,
  libraryDependencies += "org.example" %% "bippy" % "1.0-SNAPSHOT"
)

InputKey[Unit]("check") := {
  import sbt.complete.DefaultParsers._
  val n = (token(Space) ~> token(Digit)).map(_.asDigit).parsed

  val jarname = "bippy_2.11-1.0-SNAPSHOT-sources.jar"
  val file1 = ivyHome.value / "cache" / "org.example" / "bippy_2.11" / "srcs" / jarname
  val file2 = ivyHome.value / "maven-cache" / "org" / "example" / "bippy_2.11" / "1.0-SNAPSHOT" / jarname
  val file = if (file1.exists()) file1 else if (file2.exists) file2 else sys error s"$jarname MIA"
  val jar = new java.util.jar.JarFile(file)
  val s = IO readStream jar.getInputStream(jar.getJarEntry("Bippy.scala"))

  val expected = s"def release = $n"
  assert(s contains expected, s"""Bippy should contain $expected, contents:\n$s""")
  ()
}
