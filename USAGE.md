# API

Ensure you have a dependency on its artifact, e.g. add in `build.sbt`,
```scala
resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies +=
  "com.github.alexarchambault" %% "coursier" % "0.1.0-SNAPSHOT"
```

Then,
```scala
import coursier._
val repositories = Seq(
  repository.mavenCentral
)

val dependencies = Set(
  Dependency(Module("com.lihaoyi", "ammonite-pprint_2.11"), "0.3.2"),
  Dependency(Module("org.scala-lang", "scala-reflect"), "2.11.6")
)


val resolution =
  resolve(dependencies, fetchFrom(repositories)).run

assert(resolution.isDone) // Check that resolution converged

// Printing the results
for (dep <- resolution.dependencies if resolution.projectsCache.contains(dep.moduleVersion))
  println(resolution.projectsCache(dep.moduleVersion))
for (dep <- resolution.dependencies if resolution.errors.contains(dep.moduleVersion))
  println(resolution.errors(dep.moduleVersion))

// Downloading them
import coursier.core.ArtifactDownloader

val dl = ArtifactDownloader(repository.mavenCentral.root, new java.io.File("cache"))
for (dep <- resolution.dependencies if resolution.projectsCache.contains(dep.moduleVersion))
  dl.artifact(dep).run.run match {
    case -\/(err) => println(s"Failed to download ${dep.moduleVersion}: $err")
    case \/-(file) => println(s"${dep.moduleVersion}: $file")
  }
```
