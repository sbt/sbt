dependencyResolution := sbt.librarymanagement.coursier.CoursierDependencyResolution(Resolver.combineDefaultResolvers(resolvers.value.toVector))
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.8")
