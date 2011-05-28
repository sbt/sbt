	import sbt._
	import Keys._

object ArtifactTest extends Build
{
	lazy val root = Project("root", file(".")) settings(
		ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache"))),
		publishTo := Some(Resolver.file("Test Publish Repo", file("test-repo"))),
		resolvers <<= (resolvers, publishTo)(_ ++ _.toList),
		projectID <<= baseDirectory { base => (if(base / "retrieve" exists) retrieveID else publishedID) },
		artifact in (Compile, packageBin) := mainArtifact,
		libraryDependencies <<= (libraryDependencies, baseDirectory) { (deps, base) => deps ++ (if(base / "retrieve" exists) publishedID :: Nil else Nil) },
			// needed to add a jar with a different type to the managed classpath
		classpathTypes := Set(tpe),
		check <<= checkTask(dependencyClasspath),
		checkFull <<= checkTask(fullClasspath)
	)

	lazy val checkFull = TaskKey[Unit]("check-full")
	lazy val check = TaskKey[Unit]("check")

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
		val loader = sbt.classpath.ClasspathUtilities.toLoader(cp.files, si.loader)
		try { Class.forName("test.Test", false, loader); () }
		catch { case _: ClassNotFoundException | _: NoClassDefFoundError => error("Dependency not retrieved properly") }
	}
}
