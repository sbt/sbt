Seq(
	autoAPIMappings in ThisBuild := true,
	publishArtifact in (ThisBuild, packageDoc) := false,
	publishArtifact in packageSrc := false,
	organization in ThisBuild := "org.example",
	version := "1.0"
)

val aPublishResolver = Def.setting {
	Resolver.file("a-resolver", baseDirectory.in(ThisBuild).value / "a-repo")
}
val aResolver = Def.setting {
	val dir = baseDirectory.in(ThisBuild).value
	"a-resolver" at s"file://${dir.getAbsolutePath}/a-repo"
}

val bResolver = Def.setting {
	val dir = baseDirectory.in(ThisBuild).value / "b-repo"
	Resolver.file("b-resolver", dir)(Resolver.defaultIvyPatterns)
}

val apiBaseSetting = apiURL := Some(apiBase(name.value))
def apiBase(projectName: String) = url(s"http://example.org/${projectName}")
def scalaLibraryBase(v: String) = url(s"http://www.scala-lang.org/api/$v/")
def addDep(projectName: String) =
	libraryDependencies += organization.value %% projectName % version.value


val checkApiMappings = taskKey[Unit]("Verifies that the API mappings are collected as expected.")

def expectedMappings = Def.task {
  val version = scalaVersion.value
  val binVersion = scalaBinaryVersion.value
	val ms = update.value.configuration(Compile).get.modules.flatMap { mod => 
		mod.artifacts.flatMap { case (a,f) =>
			val n = a.name.stripSuffix("_" + binVersion)
			n match {
				case "a" | "b" | "c" => (f, apiBase(n)) :: Nil
				case "scala-library" => (f, scalaLibraryBase(version)) :: Nil
				case _ => Nil
			}
		}
	}
	val mc = (classDirectory in (c,Compile)).value -> apiBase("c")
	(mc +: ms).toMap
}


val a = project.settings(
	apiBaseSetting,
	publishMavenStyle := true,
	publishTo := Some(aPublishResolver.value)
)

val b = project.settings(
	apiBaseSetting,
	publishMavenStyle := false,
	publishTo := Some(bResolver.value)
)

val c = project.settings(apiBaseSetting)

val d = project.dependsOn( c ).settings(
	externalResolvers := Seq(aResolver.value, bResolver.value),
	addDep("a"),
	addDep("b"),
	checkApiMappings := {
		val actual = apiMappings.in(Compile,doc).value
		println("Actual API Mappings: " + actual.mkString("\n\t", "\n\t", ""))
		val expected = expectedMappings.value
		println("Expected API Mappings: " + expected.mkString("\n\t", "\n\t", ""))
		assert(actual == expected)
	}
)
