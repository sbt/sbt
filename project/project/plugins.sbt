addSbtPlugin("io.get-coursier" % "sbt-coursier" % coursierVersion)

// important: this line is matched / substituted during releases (via sbt-release)
def coursierVersion = "1.0.0-RC3"

// required for just released things
resolvers += Resolver.sonatypeRepo("releases")
