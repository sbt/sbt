import sbt.internal.inc.classpath.ClasspathUtilities
import xsbti.AppConfiguration

lazy val checkFull = taskKey[Unit]("")
lazy val check = taskKey[Unit]("")
lazy val checkArtifact = taskKey[Unit]("")

ThisBuild / useCoursier := false
ThisBuild / scalaVersion     := "2.12.12"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"
// ThisBuild / licenses         := List(License.Apache2)

lazy val Dev = config("dev").extend(Compile)
  .describedAs("Dependencies required for development environments")

lazy val root = (project in file("."))
  .configs(Dev)
  .settings(
    ivyPaths := IvyPaths(baseDirectory.value.toString, Some((target.value / "ivy-cache").toString)),
    publishTo := Some(Resolver.file("Test Publish Repo", file("test-repo"))),
    scalaCompilerBridgeResolvers += userLocalFileResolver(appConfiguration.value),
    resolvers += baseDirectory { base => "Test Repo" at (base / "test-repo").toURI.toString }.value,
    moduleName := artifactID,
    projectID := (if (baseDirectory.value / "retrieve").exists then  retrieveID else publishedID),
    Compile / packageBin / artifact := mainArtifact,
    libraryDependencies ++= (if (baseDirectory.value / "retrieve").exists then publishedID :: Nil else Nil),
      // needed to add a jar with a different type to the managed classpath
    Compile / unmanagedClasspath ++= {
      val converter = fileConverter.value
      val xs = scalaInstance.value.libraryJars.toSeq
      xs.map(x => converter.toVirtualFile(x.toPath()): HashedVirtualFileRef)
    },
    classpathTypes := Set(tpe),

    // custom configuration artifacts
    inConfig(Dev)(Defaults.compileSettings),
    addArtifact(Dev / packageBin / artifact, Dev / packageBin),
    addArtifact(Dev / packageDoc / artifact, Dev / packageDoc),
    addArtifact(Dev / packageSrc / artifact, Dev / packageSrc),
    Dev / publishArtifact := true,
    check := checkTask(dependencyClasspath).value,
    checkFull := checkTask(fullClasspath).value,
    checkArtifact := {
      val d = (Dev / packageSrc / artifact).value
      assert(d.`type` == "src", s"${d.`type`} is not src")

      val t = (Test / packageSrc / artifact).value
      assert(t.`type` == "src", s"${t.`type`} is not src")
    }
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
def checkTask(classpath: TaskKey[Classpath]) =
  Def.task {
    val deps = libraryDependencies.value
    given FileConverter = fileConverter.value
    val cp = (Compile / classpath).value.files.map(_.toFile())
    val loader = ClasspathUtilities.toLoader(cp, scalaInstance.value.loader)
    try { Class.forName("test.Test", false, loader); () }
    catch { case _: ClassNotFoundException | _: NoClassDefFoundError => sys.error(s"Dependency not retrieved properly: $deps, $cp") }
  }

// use the user local resolver to fetch the SNAPSHOT version of the compiler-bridge
def userLocalFileResolver(appConfig: AppConfiguration): Resolver = {
  val ivyHome = appConfig.provider.scalaProvider.launcher.ivyHome
  Resolver.file("User Local", ivyHome / "local")(Resolver.defaultIvyPatterns)
}
