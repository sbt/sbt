import sbt._

class ArtifactTest(info: ProjectInfo) extends DefaultProject(info)
{
	// use cache specific to this test
	override def ivyCacheDirectory = Some(outputPath / "ivy-cache")

	// define a test repository to publish to
	override def managedStyle = ManagedStyle.Maven
	val publishTo = Resolver.file("Test Publish Repo", "test-repo" asFile)

	// include the publishTo repository, which is normally excluded
	override def ivyRepositories = publishTo :: Nil

	// define strings for defining the artifact
	override def artifactID = "test"
	def ext = "test2"
	def classifier = "test3"
	def tpe = "test1"
	def vers = "1.1"
	def org = "test"
	// define the jar
	override def jarPath = outputPath / (artifactID + "-" + vers + "-" + classifier + "." + ext)
	def artifact = Artifact(artifactID, tpe, ext, classifier)
	
	// define the IDs to use for publishing and retrieving
	def publishedID = org % artifactID % vers artifacts(artifact)
	def retrieveID = org % "test-retrieve" % "2.0"
	
	override def projectID = if(retrieve) retrieveID else publishedID
	override def libraryDependencies = if(retrieve) Set(publishedID) else super.libraryDependencies
	
	// switches between publish and retrieve mode
	def retrieve = "retrieve".asFile.exists
	// needed to add a jar with a different extension to the classpath
	override def classpathFilter = "*." + ext
	
	// check that the test class is on the compile classpath, either because it was compiled or because it was properly retrieved
	lazy val check  = task { check0 }
	def check0 =
		try { Class.forName("test.Test", false, loader); None }
		catch { case _: ClassNotFoundException | _: NoClassDefFoundError => Some("Dependency not retrieved properly") }
	def loader = ClasspathUtilities.toLoader(compileClasspath, buildScalaInstance.loader)
}
