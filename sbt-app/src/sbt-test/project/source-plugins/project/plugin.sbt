lazy val proguard = RootProject(uri("https://github.com/sbt/sbt-proguard.git#95b27788a5b00ab89e8ae7c05ef5bfe538129280"))
lazy val git = RootProject(uri("https://github.com/sbt/sbt-git.git#a81a110af1c5693cd3fd0204248f5c529a43a112"))

lazy val root = (project in file(".")).
  dependsOn(proguard, git)
