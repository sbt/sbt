import sbt.classpath.ClasspathUtilities

lazy val checkFull = taskKey[Unit]("")
lazy val check = taskKey[Unit]("")

lazy val root = (project in file(".")).
  settings(
    ivyPaths := ((baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache")))).value,
    publishTo := Some(Resolver.file("Test Publish Repo", file("test-repo"))),
    resolvers += (baseDirectory { base => "Test Repo" at (base / "test-repo").toURI.toString }).value,
    moduleName := artifactID,
    projectID := (baseDirectory { base => (if(base / "retrieve" exists) retrieveID else publishedID) }).value,
    artifact in (Compile, packageBin) := mainArtifact,
    libraryDependencies := ((libraryDependencies, baseDirectory) { (deps, base) =>
      deps ++ (if(base / "retrieve" exists) publishedID :: Nil else Nil) }).value,
      // needed to add a jar with a different type to the managed classpath
    unmanagedClasspath in Compile += scalaInstance.map(_.libraryJar).value,
    classpathTypes := Set(tpe),
    check := checkTask(dependencyClasspath).value,
    checkFull := checkTask(fullClasspath).value
  )

// define strings for defining the artifact
def artifactID = "test"
def ext = "test2"
def classifier = "test3"
def tpe = "test1"
def vers = "1.1"
def org = "test"

def mainArtifact = Artifact(artifactID, tpe, ext, classifier)

// define the IDs to use for publishing and retrieving
def publishedID = org % artifactID % vers artifacts(mainArtifact)
def retrieveID = org % "test-retrieve" % "2.0"

// check that the test class is on the compile classpath, either because it was compiled or because it was properly retrieved
def checkTask(classpath: TaskKey[Classpath]) = (classpath in Compile, scalaInstance) map { (cp, si) =>
  val loader = ClasspathUtilities.toLoader(cp.files, si.loader)
  try { Class.forName("test.Test", false, loader); () }
  catch { case _: ClassNotFoundException | _: NoClassDefFoundError => sys.error("Dependency not retrieved properly") }
}
