	import sbt._
	import Keys._

object ArtifactTest extends Build
{
	lazy val projects = Seq(root)
	lazy val root = Project("root", file(".")) settings(
		ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t))),
		publishTo := Some(Resolver.file("Test Publish Repo", file("test-repo"))),
		resolvers <<= (resolvers, publishTo)(_ ++ _.toList),
		jarName in Compile := ArtifactName(base := artifactID, version = vers, tpe = classifier, ext = ext, cross = "", config = ""),
		projectID := (if(retrieve) retrieveID else publishedID),
		artifacts := artifact :: Nil
		libraryDependencies ++= (if(retrieve) publishedID :: Nil else Nil),
			// needed to add a jar with a different extension to the classpath
		classpathFilter := "*." + ext,
		check <<= checkTask
	)

	lazy val check = TaskKey[Unit]("check")

	// define strings for defining the artifact
	def artifactID = "test"
	def ext = "test2"
	def classifier = "test3"
	def tpe = "test1"
	def vers = "1.1"
	def org = "test"

	def artifact = Artifact(artifactID, tpe, ext, classifier)
	
	// define the IDs to use for publishing and retrieving
	def publishedID = org % artifactID % vers artifacts(artifact)
	def retrieveID = org % "test-retrieve" % "2.0"
	
	// check that the test class is on the compile classpath, either because it was compiled or because it was properly retrieved
	def checkTask = (fullClasspath in Compile, scalaInstance) map { (cp, si) =>
		val loader = classpath.ClasspathUtilities.toLoader(cp.files, si.loader)
		try { Class.forName("test.Test", false, loader) }
		catch { case _: ClassNotFoundException | _: NoClassDefFoundError => error("Dependency not retrieved properly") }
	}
}
