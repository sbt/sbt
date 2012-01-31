libraryDependencies <<= (libraryDependencies, appConfiguration) { (deps, conf) =>
	deps :+ ("org.scala-sbt" %% "sbt" % conf.provider.id.version)
}