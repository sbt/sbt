ivyConfiguration := Def.uncached {
  throw new RuntimeException("updateSbtClassifiers should use updateSbtClassifiers / ivyConfiguration")
}

dependencyResolution := Def.uncached {
  throw new RuntimeException("updateSbtClassifiers should use updateSbtClassifiers / dependencyResolution")
}

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.11.12",
    scalaOrganization := "doesnt.exist",
    name := "myProjectName",

    TaskKey[Unit]("checkModuleIdsInUpdateSbtClassifiers") := Def.uncached {
      val updateReport = updateSbtClassifiers.value
      val moduleReports = updateReport.configurations.find(_.configuration.name == "default").get.modules

      // Calling "distinct" as there are different entries for sources and javadoc classifiers with same module
      val moduleIds = moduleReports.map(_.module).distinct
      val moduleIdsShort = moduleIds
        .filter(m => m.name != "launcher-interface") // I get different result locally
        .filter(m => m.organization != "org.scala-sbt")
        .map(m => s"${m.organization}:${m.name}")

      val expectedModuleIds = Seq(
        "com.eed3si9n:gigahorse-apache-http_3",
        "com.eed3si9n:gigahorse-core_3",
        "com.eed3si9n:shaded-apache-httpclient5",
        "com.eed3si9n:shaded-jawn-parser_3",
        "com.eed3si9n:shaded-scalajson_3",
        "com.eed3si9n:sjson-new-core_3",
        "com.eed3si9n:sjson-new-murmurhash_3",
        "com.eed3si9n:sjson-new-scalajson_3",
        "com.github.ben-manes.caffeine:caffeine",
        "com.github.mwiede:jsch",
        "com.google.errorprone:error_prone_annotations",
        "com.lmax:disruptor",
        "com.swoval:file-tree-views",
        "com.typesafe:config",
        "com.typesafe:ssl-config-core_3",
        "junit:junit",
        "net.java.dev.jna:jna",
        "net.java.dev.jna:jna-platform",
        "org.checkerframework:checker-qual",
        "org.fusesource.jansi:jansi",
        "org.hamcrest:hamcrest-core",
        "org.jline:jline-builtins",
        "org.jline:jline-native",
        "org.jline:jline-reader",
        "org.jline:jline-style",
        "org.jline:jline-terminal",
        "org.jline:jline-terminal-jni",
        "org.reactivestreams:reactive-streams",
        "org.scala-lang.modules:scala-asm",
        "org.scala-lang.modules:scala-collection-compat_3",
        "org.scala-lang.modules:scala-parallel-collections_3",
        "org.scala-lang.modules:scala-parser-combinators_3",
        "org.scala-lang.modules:scala-xml_3",
        "org.scala-lang:scala-library",
        "org.scala-lang:scala3-compiler_3",
        "org.scala-lang:scala3-interfaces",
        "org.scala-lang:scala3-library_3",
        "org.scala-lang:tasty-core_3",
        "org.scala-sbt.ipcsocket:ipcsocket",
        "org.scala-sbt.ivy:ivy",
        "org.scala-sbt.jline:jline",
        "org.scala-sbt.gson:shaded-gson",
        "org.slf4j:slf4j-api",
      )
      def assertCollectionsEqual(message: String, expected: Seq[String], actual: Seq[String]): Unit =
        // using the new line for a more readable comparison failure output
        assert(expected.mkString("\n") == actual.mkString("\n"), message + ": " + actual)

      assertCollectionsEqual(
        "Unexpected module ids in updateSbtClassifiers",
        expectedModuleIds.sorted,
        moduleIdsShort.sorted,
      )
    }
  )