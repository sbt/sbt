libraryDependencies <<= (libraryDependencies, appConfiguration) { (deps, conf) =>
	deps :+ ("org.scala-tools.sbt" %% "sbt" % conf.provider.id.version)
}