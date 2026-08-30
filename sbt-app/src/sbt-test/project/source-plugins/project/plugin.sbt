lazy val git = RootProject(uri("https://github.com/sbt/sbt-git.git#047e2800186c82c1c8c65e130542be46c9ce3223"))

lazy val root = (project in file(".")).
  dependsOn(git)
