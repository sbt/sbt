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

// Cache for metadata can be setup here, see cli/src/main/scala/coursier/Coursier.scala
val repositories = Seq(
  Repository.ivy2Local,
  Repository.mavenCentral
)

val dependencies = Set(
  Dependency(Module("com.lihaoyi", "ammonite-pprint_2.11"), "0.3.2"),
  Dependency(Module("org.scala-lang", "scala-reflect"), "2.11.6")
)


val resolution =
  Resolution(dependencies)
    .process           // Process ADT, allows to go from a Resolution to the next
    .run(repositories) // Running the process, fetching metadata from these repositories
    .run               // Run the Task[Resolution], and get the resulting Resolution

// Note that only metadata are downloaded during resolution
// Repositories use scalaz Task-s. Thus the line .run(repositories) implicitily
//   runs the resolution process within Task-s. Alternative monads could run the process too.

assert(resolution.isDone) // Check that resolution converged

// Errors in
resolution.errors // Seq[(Dependency, Seq[String])], the Seq[String] contains the errors returned by each repository

// Artifact URLs in
resolution.artifacts // Seq[Artifact]
// Artifact has in particular a field  url: String


// Now if we want to download or cache the artifacts, add in build.sbt
//   "com.github.alexarchambault" %% "coursier-files" % "0.1.0-SNAPSHOT"

val files = Files(
  Seq(
    Repository.mavenCentral.fetch.root -> // URL with this prefix cached in directory:
      new java.io.File(sys.props("user.home") + "/.coursier/cache/central/files")
  ),
  () => ???, // TODO Tmp directory for URLs with no cache
  Some(logger) // Optional,  logger: FilesLogger
)

val cachePolicy = Repository.CachePolicy.Default

for (artifact <- artifacts)
  files.file(artifact, cachePolicy).run match {
    case -\/(err) => // Download failed,  err: String
    case \/-(file) => // Success,  file: java.io.File
  }

// Artifacts can be downloaded in parallel thanks to Task.gatherUnordered
// See the example in cli/src/main/scala/coursier/Coursier.scala
```
