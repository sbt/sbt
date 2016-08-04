organization := "org.dummy"

scalaVersion := "2.12.0-M5"

libraryDependencies += "com.chuusai" %% "shapeless" % "2.3.1"

resolvers += Resolver.file("buggy", (baseDirectory in LocalRootProject).value / "repo")(
  Patterns(
    ivyPatterns = Seq("[organization]/[module]/[revision]/ivy.xml"),
    artifactPatterns = Seq("[organization]/[module]/[revision]/dummy.jar"),
    isMavenCompatible = false,
    descriptorOptional = true,
    skipConsistencyCheck = true
  )
)

val checkDependencies = taskKey[Unit]("Checks that dependcies are correct.")

checkDependencies := {
  val expected: Set[ModuleID] = Set(
    "org.other"              % "scala-library"          % "2.12.0-M5-bin-other-patch-1",
    "org.other"              % "scala-compiler"         % "2.12.0-M5-bin-other-patch-1",
    "org.other"              % "scala-reflect"          % "2.12.0-M5-bin-other-patch-1",
    "jline"                  % "jline"                  % "2.14.1",
    "org.scala-lang.modules" % "scala-xml_2.12.0-M5"    % "1.0.5",
    "org.typelevel"          % "macro-compat_2.12.0-M5" % "1.1.1",
    "com.chuusai"            % "shapeless_2.12.0-M5"    % "2.3.1"
  )

  val resolved: Set[ModuleID] =
    (for {
      c <- update.value.configurations
      m <- c.modules
      if !m.evicted
    } yield m.module.copy(extraAttributes = Map.empty)).toSet

  println("Expected")
  expected.foreach(println)
  println("Resolved")
  resolved.foreach(println)
  assert(resolved == expected)
}
