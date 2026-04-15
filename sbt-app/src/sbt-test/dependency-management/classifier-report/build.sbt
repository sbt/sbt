lazy val check = taskKey[Unit]("check classifier in update report")

lazy val root = (project in file(".")).settings(
  scalaVersion := "2.13.16",
  libraryDependencies += ("io.netty" % "netty-transport-native-epoll" % "4.1.118.Final").classifier("linux-x86_64"),
  check := {
    val report = update.value
    val modules = report.configurations.flatMap(_.modules)
    val nettyModule = modules.find(_.module.name == "netty-transport-native-epoll")
      .getOrElse(sys.error("netty-transport-native-epoll not found in update report"))
    val explicitArts = nettyModule.module.explicitArtifacts
    assert(explicitArts.nonEmpty, s"Expected non-empty explicitArtifacts, got: $explicitArts")
    val classifiers = explicitArts.flatMap(_.classifier)
    assert(classifiers.contains("linux-x86_64"),
      s"Expected classifier 'linux-x86_64' in explicitArtifacts, got: $classifiers")
  },
)
