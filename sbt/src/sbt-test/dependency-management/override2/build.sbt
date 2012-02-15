sbtBinaryVersion := "0.11.2"

addSbtPlugin("com.typesafe.sbtscalariform" % "sbtscalariform" % "0.3.0")

resolvers += Classpaths.typesafeResolver

dependencyOverrides := Set("com.typesafe.sbtscalariform" % "sbtscalariform" % "0.3.1")
