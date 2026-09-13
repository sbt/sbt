lazy val root = (project in file("."))
  .settings(
    // Verify that local plugins work
    TaskKey[Unit]("checkLocalPlugins") := Def.uncached {
      val localResult = localPluginCheck.value
      val customResult = customPluginCheck.value
      assert(localResult == "local-plugin-active", s"Expected local plugin to be active, got: $localResult")
      assert(customResult == "custom plugin", s"Expected custom plugin to be active, got: $customResult")
    },

    // Verify that the dependencies in updateSbtClassifiers / classifiersModule do not include the local plugins but do include
    // other declared dependencies
    TaskKey[Unit]("checkClassifiersModule") := Def.uncached {
      val mod = (updateSbtClassifiers / classifiersModule).value
      val deps = mod.dependencies
      val actual = deps
        .filter(m => m.organization != "org.scala-sbt")
        .map(m => s"${m.organization}:${m.name}")
        .sorted.toSet

      val expected = Set(
        "junit:junit",
        "com.eed3si9n:sbt-buildinfo_sbt2_3",
        "org.hamcrest:hamcrest-core",
        "com.eed3si9n.manifesto:manifesto_3",
        "org.scala-lang:scala3-library_3",
        "org.scala-lang:scala-library",
        "org.typelevel:cats-core_3",
        "org.typelevel:cats-kernel_3"
      )

      assert(
        actual == expected,
        s"""
           |ClassifiersModule dependencies mismatch.
           |Expected: ${expected.mkString(", ")}
           |Actual:   ${actual.mkString(", ")}
          """.stripMargin
      )
    }
  )
