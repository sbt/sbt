name := "foo-lib"

organization := "sbt"

publishTo := Some(Resolver.file("test-resolver", file("").getCanonicalFile / "ivy"))

version := "0.1.0"

classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Dependencies

Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Dependencies
