organization := "org.dummy"

scalaOrganization := "org.foo"

scalaVersion := "2.99.0"

resolvers += Resolver.file("buggy", (baseDirectory in LocalRootProject).value / "repo")(
  Patterns(
    ivyPatterns = Seq("[organization]/[module]/[revision]/ivy.xml"),
    artifactPatterns = Seq("[organization]/[module]/[revision]/dummy.jar"),
    isMavenCompatible = false,
    descriptorOptional = true,
    skipConsistencyCheck = true
  )
)

libraryDependencies += "org.scala-lang.modules" % "scala-parser-combinators_2.11" % "1.0.5"

// Adapted from the scala-organization test
val checkDependencies = taskKey[Unit]("Checks that dependencies are correct.")

checkDependencies := {
  val expected: Set[ModuleID] = Set(
    "org.foo" % "scala-library" % "2.99.0",
    "org.foo" % "scala-compiler" % "2.99.0",

    "org.scala-lang" % "scala-library" % "2.11.8",
    "org.scala-lang" % "scala-compiler" % "2.11.8",
    "org.scala-lang" % "scala-reflect" % "2.11.8",

    "org.scala-lang.modules" % "scala-parser-combinators_2.11" % "1.0.5",
    "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.4",
    "org.scala-lang.modules" % "scala-parser-combinators_2.11" % "1.0.4"
  )

  val resolved: Set[ModuleID] =
    (for {
      c <- update.value.configurations
      m <- c.modules
      if !m.evicted
    } yield m.module.copy(extraAttributes = Map.empty)).toSet
  assert(resolved == expected)
}
