import sbt._

class Test(info: ProjectInfo) extends DefaultProject(info)
{
	override def ivyCacheDirectory = Some(outputPath / "ivy-cache")
	
  override def managedStyle = ManagedStyle.Maven
	def testRepoPath = path("test-repo")
	val publishTo = Resolver.file("test repo", testRepoPath asFile)
	
	def srcExt = "-sources.jar"
	def srcFilter = extFilter(srcExt)
	def docExt = "-javadoc.jar"
	def docFilter = extFilter(docExt)
	def extFilter(ext: String) = "*" + ext
	
	override def packageDocsJar = defaultJarPath(docExt)
	override def packageSrcJar= defaultJarPath(srcExt)
	
	val sourceArtifact = Artifact(artifactID, "src", "jar", "sources")
	val docsArtifact = Artifact(artifactID, "docs", "jar", Some("javadoc"), Nil, None)
	override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageDocs, packageSrc)
	
	lazy val check = task { check0 }
	
	def check0 = checkPom orElse checkBin orElse checkSource orElse checkDoc
	def checkPom = exists("pom", "*.pom")
	def checkDoc = exists("javadoc", docFilter)
	def checkSource = exists("sources", srcFilter)
	def checkBin = exists("binary", "*.jar" - (srcFilter | docFilter))
	def exists(label: String, filter: sbt.NameFilter) = 
		if( (testRepoPath ** filter).get.isEmpty) Some("No " + label + " published") else None
}