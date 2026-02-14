lazy val root = (project in file("."))
  .settings(
    // Inject a non-existent module into the classifiers module dependencies
    // to simulate a scenario where classifier artifacts can't be downloaded.
    // With missingOk=true, updateSbtClassifiers should still succeed.
    updateSbtClassifiers / classifiersModule := {
      val mod = (updateSbtClassifiers / classifiersModule).value
      val fakeModule = "com.example.nonexistent" % "fake-library" % "0.0.1"
      mod.withDependencies(mod.dependencies :+ fakeModule)
    },
  )
