import sbt._

class PomRepoTest(info: ProjectInfo) extends DefaultProject(info)
{
	val local = "local-maven-repo" at "file://" + (Path.userHome / ".m2" /"repository").absolutePath
	val remote = ScalaToolsSnapshots

	override def pomIncludeRepository(r: MavenRepository) =
		if("repo.none".asFile.exists) false else if("repo.all".asFile.exists) true else super.pomIncludeRepository(r)

	lazy val checkPom =
		task { args => task { checkPomRepositories(args.toList) } dependsOn(makePom) }

	def checkPomRepositories(args: List[String]): Option[String] =
	{
		val repositories = scala.xml.XML.loadFile(pomPath asFile) \\ "repository"
		val extracted = repositories.map { repo => MavenRepository(repo \ "name" text, repo \ "url" text) }
		val expected = args.map(GlobFilter.apply)
		log.info("Extracted: " + extracted.mkString("\n\t", "\n\t", "\n"))
		log.info("Expected: " + args.mkString("\n\t", "\n\t", "\n"))
		(extracted.find { e => !expected.exists(_.accept(e.root)) } map { "Repository should not be exported: " + _ }) orElse
			(expected.find { e => !extracted.exists(r => e.accept(r.root)) } map { "Repository should be exported: " + _ } )
	}
}