name := "foo-lib"

organization := "sbt"

publishTo := Some(Resolver.file("test-resolver", file("").getCanonicalFile / "ivy"))
