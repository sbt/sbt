	import sbt._
	import Keys._
	import StringUtilities.normalize

object Util
{
	val ExclusiveTest = Tags.Tag("exclusive-test")
	lazy val componentID = SettingKey[Option[String]]("component-id")
	lazy val scalaKeywords = TaskKey[Set[String]]("scala-keywords")
	lazy val generateKeywords = TaskKey[File]("generateKeywords")
	lazy val includeTestDependencies = SettingKey[Boolean]("includeTestDependencies", "Doesn't declare test dependencies.")

	def inAll(projects: => Seq[ProjectReference], key: ScopedSetting[Task[Unit]]): Project.Initialize[Task[Unit]] =
		inAllProjects(projects, key) { deps => nop dependsOn( deps : _*) }

	def inAllProjects[T](projects: => Seq[ProjectReference], key: ScopedSetting[T]): Project.Initialize[Seq[T]] =
		Project.bind( (loadedBuild, thisProjectRef).identity ) { case (lb, pr) =>
			def resolve(ref: ProjectReference): ProjectRef = Scope.resolveProjectRef(pr.build, Load.getRootProject(lb.units), ref)
			val refs = projects flatMap { base => Defaults.transitiveDependencies(resolve(base.project), lb, includeRoot=true, classpath=true, aggregate=true) }
			refs map ( ref => (key in ref).? ) joinWith(_ flatMap { x => x})
		}

	def noPublish(p: Project) = p.copy(settings = noRemotePublish(p.settings))
	def noRemotePublish(in: Seq[Setting[_]]) = in filterNot { s => s.key == deliver || s.key == publish }

	def project(path: File, nameString: String) = Project(normalize(nameString), path) settings( Seq(name := nameString) ++ publishPomSettings : _* )
	def baseProject(path: File, nameString: String) = project(path, nameString) settings( base : _*)
	def testedBaseProject(path: File, nameString: String) = baseProject(path, nameString) settings(includeTestDependencies := true, testDependencies)
	
	lazy val javaOnly = Seq[Setting[_]](/*crossPaths := false, */compileOrder := CompileOrder.JavaThenScala, unmanagedSourceDirectories in Compile <<= Seq(javaSource in Compile).join)
	lazy val base: Seq[Setting[_]] = baseScalacOptions ++ Licensed.settings
	lazy val baseScalacOptions = Seq(
		projectComponent,
		scalacOptions ++= Seq("-Xelide-below", "0"),
		scalacOptions <++= scalaVersion map CrossVersion.partialVersion map {
			case Some((2, 9)) => Nil // support 2.9 for some subprojects for the Scala Eclipse IDE
			case _ => Seq("-feature", "-language:implicitConversions", "-language:postfixOps", "-language:higherKinds", "-language:existentials")
		}
	)
	
	def testDependencies = libraryDependencies <++= includeTestDependencies { incl =>
		if(incl) Seq(
			"org.scalacheck" %% "scalacheck" % "1.10.0" % "test",
			"org.specs2" %% "specs2" % "1.12.3" % "test"
		)
		else Seq()
	}

	lazy val minimalSettings: Seq[Setting[_]] = Defaults.paths ++ Seq[Setting[_]](crossTarget <<= target.identity, name <<= thisProject(_.id))

	def projectComponent = projectID <<= (projectID, componentID) { (pid, cid) => 
		cid match { case Some(id) => pid extra("e:component" -> id); case None => pid }
	}

	lazy val apiDefinitions = TaskKey[Seq[File]]("api-definitions")

	def generateAPICached(cache: File, defs: Seq[File], cp: Classpath, out: File, main: Option[String], run: ScalaRun, s: TaskStreams): Seq[File] =
	{
		def gen() = generateAPI(defs, cp, out, main, run, s)
		val f = FileFunction.cached(cache / "gen-api", FilesInfo.hash) { _ => gen().toSet} // TODO: check if output directory changed
		f(defs.toSet).toSeq
	}
	def generateAPI(defs: Seq[File], cp: Classpath, out: File, main: Option[String], run: ScalaRun, s: TaskStreams): Seq[File] =
	{
		IO.delete(out)
		IO.createDirectory(out)
		val args = "immutable" :: "xsbti.api" :: out.getAbsolutePath :: defs.map(_.getAbsolutePath).toList
		val mainClass = main getOrElse "No main class defined for datatype generator"
		toError(run.run(mainClass, cp.files, args, s.log))
		(out ** "*.java").get
	}
	def lastCompilationTime(analysis: sbt.inc.Analysis): Long =
	{
		val times = analysis.apis.internal.values.map(_.compilation.startTime)
		if(times.isEmpty) 0L else times.max
	}
	def generateVersionFile(version: String, dir: File, s: TaskStreams, analysis: sbt.inc.Analysis): Seq[File] =
	{
		import java.util.{Date, TimeZone}
		val formatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")
		formatter.setTimeZone(TimeZone.getTimeZone("GMT"))
		val timestamp = formatter.format(new Date)
		val content = versionLine(version) + "\ntimestamp=" + timestamp
		val f = dir / "xsbt.version.properties"
		if(!f.exists || f.lastModified < lastCompilationTime(analysis) || !containsVersion(f, version)) {
			s.log.info("Writing version information to " + f + " :\n" + content)
			IO.write(f, content)
		}
		f :: Nil
	}
	def versionLine(version: String): String = "version=" + version
	def containsVersion(propFile: File, version: String): Boolean = IO.read(propFile).contains(versionLine(version))

	def binID = "compiler-interface-bin"
	def srcID = "compiler-interface-src"

	def publishPomSettings: Seq[Setting[_]] = Seq(
		publishArtifact in makePom := false,
		pomPostProcess := cleanPom _
	)

	def cleanPom(pomNode: scala.xml.Node) =
	{
		import scala.xml._
		def cleanNodes(nodes: Seq[Node]): Seq[Node] = nodes flatMap ( _ match {
			case elem @ Elem(prefix, "dependency", attributes, scope, children @ _*) if excludePomDependency(elem) =>
				NodeSeq.Empty
			case Elem(prefix, "classifier", attributes, scope, children @ _*) =>
				NodeSeq.Empty
			case Elem(prefix, label, attributes, scope, children @ _*) =>
				Elem(prefix, label, attributes, scope, cleanNodes(children): _*).theSeq
			case other => other
		})
		cleanNodes(pomNode.theSeq)(0)
	}

	def excludePomDependency(node: scala.xml.Node) = node \ "artifactId" exists { n => excludePomArtifact(n.text) }

	def excludePomArtifact(artifactId: String) = (artifactId == "compiler-interface") || (artifactId startsWith "precompiled")

	val testExclusive = tags in test += ( (ExclusiveTest, 1) )

	// TODO: replace with Tags.exclusive after 0.12.0
	val testExclusiveRestriction = Tags.customLimit { (tags: Map[Tags.Tag,Int]) =>
		val exclusive = tags.getOrElse(ExclusiveTest, 0)
		val all = tags.getOrElse(Tags.All, 0)
		exclusive == 0 || all == 1
	}

	def getScalaKeywords: Set[String] =
	{
		val g = new scala.tools.nsc.Global(new scala.tools.nsc.Settings)
		g.nme.keywords.map(_.toString)
	}
	def writeScalaKeywords(base: File, keywords: Set[String]): File =
	{
		val init = keywords.map(tn => '"' + tn + '"').mkString("Set(", ", ", ")")
		val ObjectName = "ScalaKeywords"
		val PackageName = "sbt"
		val keywordsSrc = 
"""package %s
object %s {
	val values = %s
}""".format(PackageName, ObjectName, init)
		val out = base / PackageName.replace('.', '/') / (ObjectName + ".scala")
		IO.write(out, keywordsSrc)
		out
	}
	def keywordsSettings: Seq[Setting[_]] = inConfig(Compile)(Seq(
		scalaKeywords := getScalaKeywords,
		generateKeywords <<= (sourceManaged, scalaKeywords) map writeScalaKeywords,
		sourceGenerators <+= generateKeywords map (x => Seq(x))
	))
}
object Common
{
	def lib(m: ModuleID) = libraryDependencies += m
	lazy val jlineDep = "jline" % "jline" % "2.11"
	lazy val jansiDep = "org.fusesource.jansi" % "jansi" % "1.11" // jline pom doesn't explicitly declare it?
	lazy val jline = Seq(lib(jlineDep), lib(jansiDep))
	lazy val ivy = lib("org.apache.ivy" % "ivy" % "2.3.0-rc1")
	lazy val httpclient = lib("commons-httpclient" % "commons-httpclient" % "3.1")
	lazy val jsch = lib("com.jcraft" % "jsch" % "0.1.46" intransitive() )
	lazy val sbinary = lib("org.scala-tools.sbinary" %% "sbinary" % "0.4.1" )
	lazy val scalaCompiler = libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _ )
}
object Licensed
{
	lazy val notice = SettingKey[File]("notice")
	lazy val extractLicenses = TaskKey[Seq[File]]("extract-licenses")

	lazy val seeRegex = """\(see (.*?)\)""".r
	def licensePath(base: File, str: String): File = { val path = base / str; if(path.exists) path else error("Referenced license '" + str + "' not found at " + path) }
	def seePaths(base: File, noticeString: String): Seq[File] = seeRegex.findAllIn(noticeString).matchData.map(d => licensePath(base, d.group(1))).toList
	
	def settings: Seq[Setting[_]] = Seq(
		notice <<= baseDirectory(_ / "NOTICE"),
		unmanagedResources in Compile <++= (notice, extractLicenses) map { _ +: _ },
		extractLicenses <<= (baseDirectory in ThisBuild, notice, streams) map extractLicenses0
	)
	def extractLicenses0(base: File, note: File, s: TaskStreams): Seq[File] =
		if(!note.exists) Nil else
			try { seePaths(base, IO.read(note)) }
			catch { case e: Exception => s.log.warn("Could not read NOTICE"); Nil }
}
