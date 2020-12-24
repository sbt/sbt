ThisBuild / useCoursier := false

scalaOrganization := "org.other"
scalaVersion := "3.0.0-M2"

resolvers += Resolver.file("buggy", (baseDirectory in LocalRootProject).value / "repo")(
  Patterns(
    ivyPatterns = Vector("[organization]/[module]/[revision]/ivy.xml"),
    artifactPatterns = Vector("[organization]/[module]/[revision]/dummy.jar"),
    isMavenCompatible = false,
    descriptorOptional = true,
    skipConsistencyCheck = true
  )
)

libraryDependencies += "org.typelevel" %% "cats-core" % "2.3.0"

val checkDependencies = taskKey[Unit]("Checks that dependencies are correct.")

checkDependencies := {
  val expected: Set[ModuleID] = Set(
    "org.scala-lang.modules" % "scala-asm" % "7.3.1-scala-1",
    "org.jline" % "jline-reader" % "3.15.0",
    "com.google.protobuf" % "protobuf-java" % "3.7.0",
    "org.typelevel" % "cats-kernel_3.0.0-M2" % "2.3.0",
    "org.jline" % "jline-terminal-jna" % "3.15.0",
    "org.jline" % "jline-terminal" % "3.15.0",
    "org.scala-sbt" % "compiler-interface" % "1.3.5",
    "net.java.dev.jna" % "jna" % "5.3.1",
    "org.other" % "scala-library" % "2.13.4",
    "org.other" % "scala3-library_3.0.0-M2" % "3.0.0-M2",
    "org.typelevel" % "simulacrum-scalafix-annotations_3.0.0-M2" % "0.5.1",
    "org.other" % "scala3-compiler_3.0.0-M2" % "3.0.0-M2",
    "org.other" % "scala3-interfaces" % "3.0.0-M2",
    "org.other" % "tasty-core_3.0.0-M2" % "3.0.0-M2",
    "org.typelevel" % "cats-core_3.0.0-M2" % "2.3.0",
    "org.scala-sbt" % "util-interface" % "1.3.0"
  )

  val resolved: Set[ModuleID] =
    (for {
      c <- update.value.configurations
      m <- c.modules
      if !m.evicted
    } yield m.module.withExtraAttributes(Map.empty)).toSet

  assert(resolved == expected, s"$resolved != $expected")
}
