import complete.DefaultParsers._

lazy val root = (project in file(".")).
  settings(
    resolvers ++= Seq(local, Resolver.sonatypeRepo("releases"), Resolver.sonatypeRepo("snapshots")),
    InputKey[Unit]("checkPom") := {
      val result = spaceDelimited("<args>").parsed
      checkPomRepositories(makePom.value, result, streams.value)
    },
    makePomConfiguration := {
      val conf = makePomConfiguration.value
      conf
        .withFilterRepositories(pomIncludeRepository(baseDirectory.value, conf.filterRepositories))
    },
    ivyPaths := baseDirectory( dir => IvyPaths(dir, Some(dir / "ivy-home"))).value
  )

val local = "local-maven-repo" at "file://" + (Path.userHome / ".m2" /"repository").absolutePath

def pomIncludeRepository(base: File, prev: MavenRepository => Boolean): MavenRepository => Boolean = {
  case r: MavenRepository if (r.name == "local-preloaded") => false
  case r: MavenRepository if (base  / "repo.none" exists)  => false
  case r: MavenRepository if (base / "repo.all" exists)    => true
  case r: MavenRepository => prev(r)
}

def addSlash(s: String): String = s match {
  case s if s endsWith "/" => s
  case _ => s + "/"
}

def checkPomRepositories(file: File, args: Seq[String], s: TaskStreams) {
  val repositories = scala.xml.XML.loadFile(file) \\ "repository"
  val extracted = repositories.map { repo => MavenRepository(repo \ "name" text, addSlash(repo \ "url" text)) }
  val expected = args.map(GlobFilter.apply)
  s.log.info("Extracted: " + extracted.mkString("\n\t", "\n\t", "\n"))
  s.log.info("Expected: " + args.mkString("\n\t", "\n\t", "\n"))
  extracted.find { e => !expected.exists(_.accept(e.root)) } map { "Repository should not be exported: " + _ } orElse
    (expected.find { e => !extracted.exists(r => e.accept(r.root)) } map { "Repository should be exported: " + _ } ) foreach sys.error
}
