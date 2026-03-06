ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"
ThisBuild / organization := "org.example"
ThisBuild / version := "1.0"
ThisBuild / useIvy := true

lazy val a = project.settings(common).settings(
  // verifies that a can be published as an ivy.xml file and preserve the extra artifact information,
  //   such as a classifier
  libraryDependencies := Seq(("net.sf.json-lib" % "json-lib" % "2.4").classifier("jdk15").intransitive()),
  // verifies that an artifact without an explicit configuration gets published in all public configurations
  (Compile / packageBin / artifact) := Artifact("demo")
)

lazy val b = project.settings(common).settings(
  libraryDependencies := Seq(organization.value %% "a" % version.value)
)

def localCache =
  ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

lazy val common = Seq(
  localCache,
  autoScalaLibrary := false, // avoid downloading fresh scala-library/scala-compiler
  managedScalaInstance := false,
)
