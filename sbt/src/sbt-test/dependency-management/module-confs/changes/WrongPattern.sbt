{
	def snapshotPattern = "http://repo.typesafe.com/typesafe/scala-tools-snapshots/[organization]/[module]/2.10.a-SNAPSHOT/[artifact]-[revision].[ext]"
	def scalaSnapshots = Resolver.url("Scala Tools Snapshots") artifacts(snapshotPattern) ivys(snapshotPattern) mavenStyle()
	moduleConfigurations += ModuleConfiguration("org.scala-lang", "*", "2.10.0-.*", scalaSnapshots)
}

libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.10.0-20111001.020530-165"

resolvers := Nil