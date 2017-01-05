def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := IvyPaths( (baseDirectory in ThisBuild).value, Some((baseDirectory in LocalRootProject).value / "ivy-cache")),
    dependencyCacheDirectory := (baseDirectory in LocalRootProject).value / "dependency",
    scalaVersion := "2.10.4",
    organization in ThisBuild := "org.example",
    version in ThisBuild := "1.0-SNAPSHOT",
    resolvers += Resolver.file("old-local", file(sys.props("user.home") + "/.ivy2/local"))(Resolver.ivyStylePatterns)
  )

lazy val main = project.
  settings(commonSettings: _*).
  settings(
    uniqueName,
    libraryDependencies += (projectID in library).value,
    fullResolvers := fullResolvers.value.filterNot(_.name == "inter-project"),
    // TODO - should this not be needed?
    updateOptions := updateOptions.value.withLatestSnapshots(true)
  )

lazy val library = project.
  settings(commonSettings: _*).
  settings(
    uniqueName
  )

def uniqueName = 
  name := (name.value + "-" + randomSuffix( (baseDirectory in ThisBuild).value))

// better long-term approach to a clean cache/local
//  would be to not use the actual ~/.m2/repository
def randomSuffix(base: File) = {
  // need to persist it so that it doesn't change across reloads
  val persist = base / "suffix"
  if(persist.exists) IO.read(persist)
  else {
    val s = Hash.halfHashString(System.currentTimeMillis.toString)
    IO.write(persist, s)
    s
  }
}
