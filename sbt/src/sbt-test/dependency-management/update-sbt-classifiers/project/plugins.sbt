resolvers += Resolver.url("sbt-plugin-snapshots", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-snapshots/"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.eed3si9n" % "sbt-twt" % "0.2.1", sbtVersion = "0.12")
