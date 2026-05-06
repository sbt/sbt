lazy val git = RootProject(uri("https://github.com/sbt/sbt-git.git#66bf7f0bd51629deb0c4283cddbe8af8c30af0de"))

lazy val root = (project in file(".")).
  dependsOn(git)
