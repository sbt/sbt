addSbtPlugin("io.get-coursier" % "sbt-coursier" % coursierVersion0)

// important: this line is matched / substituted during releases (via sbt-release)
def coursierVersion0 = "1.0.1-M1"

// required for just released things
resolvers += Resolver.sonatypeRepo("releases")
