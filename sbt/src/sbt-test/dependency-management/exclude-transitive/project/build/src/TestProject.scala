import sbt._

class TestProject(info: ProjectInfo) extends DefaultProject(info)
{
	def transitive(dep: ModuleID) = if("transitive".asFile.exists) dep else dep.intransitive()
	val javaMail = transitive("javax.mail" % "mail" % "1.4.1")
	
	lazy val checkTransitive = task { check(true) }
	lazy val checkIntransitive = task { check(false) }

	private def check(transitive: Boolean) =
	{
		val downloaded = compileClasspath.get
		val jars = downloaded.size
		if(transitive)
		{
			if(jars > 1) None
			else Some("Transitive dependencies not downloaded")
		}
		else
		{
			if(jars == 1) None
			else Some("Transitive dependencies downloaded (" + downloaded.mkString(", ") + ")")
		}
	}
}
