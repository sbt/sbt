{
	def snapshotPattern = "http://scala-tools.org/repo-snapshots/[organization]/[module]/2.10.0-SNAPSHOT/[artifact]-[revision].[ext]"
	def scalaSnapshots = Resolver.url("Scala Tools Snapshots") artifacts(snapshotPattern) ivys(snapshotPattern) mavenStyle()
	moduleConfigurations += ModuleConfiguration("org.not-scala-lang", "*", "2.10.0-.*", scalaSnapshots)
}

libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.10.0-20110412.015459-16"