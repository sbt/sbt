LibraryDependencies <<= (LibraryDependencies, Project.AppConfig) { (deps, conf) =>
	deps :+ ("org.scala-tools.sbt" %% "sbt" % conf.provider.id.version)
}