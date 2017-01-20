organization := "org.dummy"

scalaOrganization := "org.other"

scalaVersion := "2.11.8"

resolvers += Resolver.file("buggy", (baseDirectory in LocalRootProject).value / "repo")(
  Patterns(
    ivyPatterns = Vector("[organization]/[module]/[revision]/ivy.xml"),
    artifactPatterns = Vector("[organization]/[module]/[revision]/dummy.jar"),
    isMavenCompatible = false,
    descriptorOptional = true,
    skipConsistencyCheck = true
  )
)

libraryDependencies += "org.typelevel" %% "cats" % "0.6.0"

val checkDependencies = taskKey[Unit]("Checks that dependencies are correct.")

checkDependencies := {
  val expected: Set[ModuleID] = Set(
    "com.github.mpilquist" % "simulacrum_2.11" % "0.7.0",
    "jline" % "jline" % "2.12.1",
    "org.other" % "scala-compiler" % "2.11.8",
    "org.other" % "scala-library" % "2.11.8",
    "org.other" % "scala-reflect" % "2.11.8",
    "org.scala-lang.modules" % "scala-parser-combinators_2.11" % "1.0.4",
    "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.5",
    "org.scala-sbt" % "test-interface" % "1.0",
    "org.scalacheck" % "scalacheck_2.11" % "1.12.5",
    "org.typelevel" % "catalysts-macros_2.11" % "0.0.2",
    "org.typelevel" % "catalysts-platform_2.11" % "0.0.2",
    "org.typelevel" % "cats-core_2.11" % "0.6.0",
    "org.typelevel" % "cats-free_2.11" % "0.6.0",
    "org.typelevel" % "cats-kernel-laws_2.11" % "0.6.0",
    "org.typelevel" % "cats-kernel_2.11" % "0.6.0",
    "org.typelevel" % "cats-laws_2.11" % "0.6.0",
    "org.typelevel" % "cats-macros_2.11" % "0.6.0",
    "org.typelevel" % "cats_2.11" % "0.6.0",
    "org.typelevel" % "discipline_2.11" % "0.4",
    "org.typelevel" % "machinist_2.11" % "0.4.1",
    "org.typelevel" % "macro-compat_2.11" % "1.1.0"
  )

  val resolved: Set[ModuleID] =
    (for {
      c <- update.value.configurations
      m <- c.modules
      if !m.evicted
    } yield m.module.withExtraAttributes(Map.empty)).toSet

  assert(resolved == expected)
}
