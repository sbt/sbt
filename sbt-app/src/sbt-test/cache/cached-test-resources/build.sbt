lazy val verify = "com.eed3si9n.verify" %% "verify" % "1.0.0"

@transient
lazy val checkResourceDigests = taskKey[Unit]("")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

ThisBuild / scalaVersion := "3.9.0"

lazy val util = project

lazy val core = project.dependsOn(util)

lazy val app = project
  .dependsOn(core)
  .settings(
    libraryDependencies += verify % Test,
    testFrameworks += new TestFramework("verify.runner.Framework"),
    Compile / unmanagedResourceDirectories += baseDirectory.value / "extra-resources",
    inConfig(Test)(checkResourceDigests := {
      val digests = sbt.internal.IncrementalTest.classpathResourceDigestsTask.value
      val expected = Seq("app.txt", "same.txt", "core.txt", "same.txt", "util.txt")
      val message = s"expected the digests of $expected, got ${digests.size}"
      assert(digests.size == expected.size, message)
    }),
  )

lazy val remapped = project
  .settings(
    libraryDependencies += verify % Runtime,
    testFrameworks += new TestFramework("verify.runner.Framework"),
    Test / classpathConfiguration := Runtime,
  )

lazy val shadowed = project
  .settings(
    exportJars := false,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "extra-resources",
    libraryDependencies += verify % Test,
    testFrameworks += new TestFramework("verify.runner.Framework"),
  )
