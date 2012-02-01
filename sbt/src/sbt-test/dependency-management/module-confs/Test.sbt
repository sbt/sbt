{
	def snapshotPattern = "http://repo.typesafe.com/typesafe/scala-tools-snapshots/[organization]/[module]/2.10.0-SNAPSHOT/[artifact]-[revision].[ext]"
	def scalaSnapshots = Resolver.url("Scala Tools Snapshots (Typesafe)") ivys(snapshotPattern) artifacts(snapshotPattern) mavenStyle()
	moduleConfigurations += ModuleConfiguration("org.scala-lang", "*", "2.10.0-.*", scalaSnapshots)
}

libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.10.0-20120122.024228-256"

resolvers := Nil