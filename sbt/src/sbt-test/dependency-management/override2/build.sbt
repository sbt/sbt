sbtBinaryVersion := "0.11.2"

addSbtPlugin("com.typesafe.sbtscalariform" % "sbtscalariform" % "0.3.0", sbtVersion = "0.11.2", scalaVersion = "2.9.1")

scalaBinaryVersion := "2.9.1"

resolvers += Classpaths.typesafeReleases

dependencyOverrides := Vector("com.typesafe.sbtscalariform" % "sbtscalariform" % "0.3.1")

autoScalaLibrary := false
