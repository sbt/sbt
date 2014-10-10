	import sbt.{Node =>_,_}
	import Keys._
	import scala.xml._

object MakePomTest extends Build
{
	lazy val root = Project("root", file(".")) settings(
		readPom <<= makePom map XML.loadFile,
		TaskKey[Unit]("check-pom") <<= checkPom,
		TaskKey[Unit]("check-extra") <<= checkExtra,
		TaskKey[Unit]("check-version-plus-mapping") <<= checkVersionPlusMapping,
		resolvers += Resolver.sonatypeRepo("snapshots"),
		makePomConfiguration ~= { _.copy(extra = <extra-tag/>) },
		libraryDependencies += "com.google.code.findbugs" % "jsr305" % "1.3.+"
	)

	val readPom = TaskKey[Elem]("read-pom")
	
	val fakeName = "fake"
	val fakeURL = "http://example.org"
	val fakeRepo = fakeName at fakeURL
	def extraTagName = "extra-tag"

	def checkProject(pom: Elem) = if(pom.label != "project") sys.error("Top level element was not 'project': " + pom.label)
	
	def withRepositories[T](pomXML: Elem)(f: NodeSeq => T) =
	{
		val repositoriesElement = pomXML \ "repositories"
		if(repositoriesElement.size == 1) f(repositoriesElement) else sys.error("'repositories' element not found in generated pom")
	}
	
	lazy val checkExtra = readPom map { pomXML =>
		checkProject(pomXML)
		val extra =  pomXML \ extraTagName
		if(extra.isEmpty) sys.error("'" + extraTagName + "' not found in generated pom.xml.") else ()
	}
	
	lazy val checkVersionPlusMapping = (readPom) map { (pomXml) =>
		var found = false
		for {
			dep <- pomXml \ "dependencies" \ "dependency"
			if (dep \ "artifactId").text == "jsr305"
			// TODO - Ignore space here.
			if (dep \ "version").text != "[1.3,1.4)"
		} sys.error(s"Found dependency with invalid maven version: $dep")
		()
	}

	lazy val checkPom = (readPom, fullResolvers) map { (pomXML, ivyRepositories) =>
		checkProject(pomXML)
		withRepositories(pomXML) { repositoriesElement =>
			val repositories =  repositoriesElement \ "repository"
			val writtenRepositories = repositories.map(read).distinct
			val mavenStyleRepositories = ivyRepositories.collect { case x: MavenRepository if x.name != "public" => normalize(x) } distinct;
			
			lazy val explain = (("Written:" +: writtenRepositories) ++ ("Declared:" +: mavenStyleRepositories)).mkString("\n\t")
			
			if( writtenRepositories != mavenStyleRepositories )
				sys.error("Written repositories did not match declared repositories.\n\t" + explain)
			else
				()
		}
	}
	
	def read(repository: Node): MavenRepository =
		(repository \ "name").text at normalize((repository \ "url").text)
		
	def normalize(url: String): String =
	{
		val base = uri( url ).normalize.toString
		if(base.endsWith("/")) base else (base + "/")
	}
	def normalize(repo: MavenRepository): MavenRepository = new MavenRepository(repo.name, normalize(repo.root))
}