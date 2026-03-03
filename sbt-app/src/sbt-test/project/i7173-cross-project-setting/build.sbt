val check = taskKey[Unit]("Verify cross-project source generator is applied")

lazy val foo = project
  .in(file("foo"))
  .settings(
    check := Def.uncached {
      val gens = (Compile / sourceGenerators).value
      assert(
        gens.nonEmpty,
        s"#7173: `foo / Compile / sourceGenerators` should contain the generator registered by fooAux"
      )
    }
  )

lazy val fooAux = project
  .in(file("foo-aux"))
  .settings(
    foo / Compile / sourceGenerators += Def.task(Seq.empty[File])
  )

lazy val bar = project

// Fails:
lazy val baz = project

// Succeeds without i7173 edits:
// lazy val ewikqelkqweqopweo = project
