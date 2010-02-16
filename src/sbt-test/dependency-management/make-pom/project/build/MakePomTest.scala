import sbt._

import scala.xml._

class MakePomTest(info: ProjectInfo) extends DefaultProject(info)
{
	val fakeName = "fake"
	val fakeURL = "http://example.org"
	val fakeRepo = fakeName at fakeURL
	
	def extraTagName = "extra-tag"
	override def pomExtra = <extra-tag/>
	def readPom = XML.loadFile(pomPath.asFile)
	def checkProject(pom: Elem): Either[String, Elem] = if(pom.label == "project") Right(pom) else Left("Top level element was not 'project': " + pom.label)
	
	def withPom(f: Elem => Either[String, Unit]) = task {
		checkProject(readPom).right.flatMap(f).left.toOption
	}
	def withRepositories[T](pomXML: Elem)(f: NodeSeq => Either[String, T]) =
	{
		val repositoriesElement = pomXML \ "repositories"
		if(repositoriesElement.size == 1) f(repositoriesElement) else Left("'repositories' element not found in generated pom")
	}
	
	
	lazy val checkExtra = withPom { pomXML =>
		val extra =  pomXML \ extraTagName
		if(extra.isEmpty) Left("'" + extraTagName + "' not found in generated pom.xml.") else Right(())
	}
	
	lazy val checkPom = withPom { pomXML =>
		withRepositories(pomXML) { repositoriesElement =>
		
			val repositories =  repositoriesElement \ "repository"
			val writtenRepositories = Set() ++ repositories.map(read)
			val mavenStyleRepositories = Set() ++ ivyRepositories.filter(x => x.isInstanceOf[MavenRepository] && x.name != "public").map(normalize)
			
			lazy val explain = ("Written:" :: writtenRepositories.toList ::: "Declared:" :: mavenStyleRepositories.toList).mkString("\n\t")
			
			if( writtenRepositories  == mavenStyleRepositories ) Right(())
			else Left("Written repositories did not match declared repositories.\n\t" + explain)
		}
	}
	
	def read(repository: Node): MavenRepository =
		(repository \ "name").text at normalize((repository \ "url").text)
		
	def normalize(url: String): String =
	{
		val base = (new java.net.URI( url )).normalize.toString
		if(base.endsWith("/")) base else (base + "/")
	}
	def normalize(repo: Resolver): Resolver = repo match { case mr: MavenRepository => normalize(mr); case _ => repo }
	def normalize(repo: MavenRepository): MavenRepository = new MavenRepository(repo.name, normalize(repo.root))
}