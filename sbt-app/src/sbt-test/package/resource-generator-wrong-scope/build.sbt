ThisBuild / scalaVersion := "3.3.1"

lazy val a = (project in file("a"))
  .settings(
    name := "a",
    crossPaths := false,
    // Intentionally uses bare `resourceManaged.value` instead of `(Compile / resourceManaged).value`.
    // Referencing a key without an explicit configuration scope inside a Def.task block does not
    // inherit the Compile scope from the `Compile / resourceGenerators` it's assigned to, so this
    // writes into the wrong (globally-scoped) resource_managed directory. That file would previously
    // be silently dropped from the packaged jar; managedResources should now fail the build instead.
    Compile / resourceGenerators += Def.task {
      val f = resourceManaged.value / "generated.txt"
      IO.write(f, "should not be silently dropped")
      Seq(f)
    },
  )

TaskKey[Unit]("checkError") := Def.uncached {
  (a / Compile / managedResources).result.value.toEither match {
    case Right(_) =>
      sys.error(
        "Expected `a / Compile / managedResources` to fail because the generator writes " +
          "outside managedResourceDirectories, but it succeeded"
      )
    case Left(inc) =>
      val msg = inc.directCause.map(_.getMessage).getOrElse(inc.toString)
      if (!msg.contains("outside managedResourceDirectories"))
        sys.error(s"Expected error mentioning 'outside managedResourceDirectories', got:\n$msg")
  }
}
